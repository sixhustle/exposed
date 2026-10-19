package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shop.common.error.DuplicateEmailException;
import com.example.shop.common.error.InsufficientStockException;
import com.example.shop.common.error.InvalidOrderStateException;
import com.example.shop.common.error.InvalidSortPropertyException;
import com.example.shop.common.error.NotFoundException;
import com.example.shop.common.error.OptimisticLockConflictException;
import com.example.shop.common.web.CursorResponse;
import com.example.shop.common.web.PageResponse;
import com.example.shop.item.BestSellerResponse;
import com.example.shop.item.ItemCreateRequest;
import com.example.shop.item.ItemResponse;
import com.example.shop.item.ItemSearchCondition;
import com.example.shop.item.ItemService;
import com.example.shop.item.ItemUpdateRequest;
import com.example.shop.order.OrderLineRequest;
import com.example.shop.order.OrderPlaceRequest;
import com.example.shop.order.OrderResponse;
import com.example.shop.order.OrderSearchCondition;
import com.example.shop.order.OrderService;
import com.example.shop.order.OrderStatus;
import com.example.shop.routing.ConnectionInfo;
import com.example.shop.routing.RoutingService;
import com.example.shop.support.ReplicatedMySql;
import com.example.shop.support.SqlRecorder;
import com.example.shop.user.UserRegisterRequest;
import com.example.shop.user.UserResponse;
import com.example.shop.user.UserService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 세 프로젝트(jpa-querydsl, exposed-dao, exposed-dsl)가 각자의 언어로 같은 시나리오를 갖는 계약 테스트.
 * 테스트 이름·순서·검증 값을 세 프로젝트에서 똑같이 유지한다 (Exposed 프로젝트는 Kotlin으로 같은 내용).
 * 스키마 검증은 기동 시 ddl-auto=validate가 한다 (Exposed 프로젝트는 별도 테스트).
 *
 * 규칙: 쓰기 다음 readOnly 조회(레플리카) 전에는 written()으로 복제를 기다린다. 실제 서비스라면 사용자가 겪을 레플리카 지연이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ShopContractTest.SqlRecordingConfig.class)
class ShopContractTest {

    private static final String IMPLEMENTATION = "jpa-querydsl";

    @Autowired UserService users;
    @Autowired ItemService items;
    @Autowired OrderService orders;
    @Autowired RoutingService routing;
    @Autowired MockMvc mockMvc;

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlRecordingConfig {
        @Bean
        static SqlRecorder.PostProcessor sqlRecordingPostProcessor() {
            return new SqlRecorder.PostProcessor();
        }
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("app.datasource.writer.jdbc-url", ReplicatedMySql::writerJdbcUrl);
        registry.add("app.datasource.writer.username", () -> "shop");
        registry.add("app.datasource.writer.password", () -> "shop");
        registry.add("app.datasource.reader.jdbc-url", ReplicatedMySql::readerJdbcUrl);
        registry.add("app.datasource.reader.username", () -> ReplicatedMySql.READER_USER);
        registry.add("app.datasource.reader.password", () -> ReplicatedMySql.READER_PASSWORD);
    }

    @BeforeEach
    void cleanDatabase() {
        ReplicatedMySql.truncateAll();
    }

    // ---------------------------------------------------------------- 라우팅

    @Test
    @DisplayName("쓰기 트랜잭션은 소스(server_id=1, shop), readOnly 트랜잭션은 레플리카(server_id=2, shop_reader)로 간다")
    void routing() {
        ConnectionInfo write = routing.inWriteTransaction();
        ConnectionInfo read = routing.inReadOnlyTransaction();

        assertThat(write.serverId()).isEqualTo(1);
        assertThat(write.user()).startsWith("shop@");
        assertThat(write.readOnly()).isFalse();
        assertThat(read.serverId()).isEqualTo(2);
        assertThat(read.user()).startsWith("shop_reader@");
        assertThat(read.readOnly()).isTrue();
    }

    @Test
    @DisplayName("소스에 쓴 데이터가 레플리카로 복제되어 readOnly 조회에서 보인다")
    void replication() {
        UserResponse user = users.register(new UserRegisterRequest("repl@shop.com", "복제"));
        written();
        assertThat(users.get(user.id())).isEqualTo(user);
    }

    // ---------------------------------------------------------------- 사용자

    @Test
    @DisplayName("같은 이메일로 두 번 가입하면 409, 먼저 가입한 사용자는 그대로")
    void duplicateEmail() {
        UserResponse first = users.register(new UserRegisterRequest("dup@shop.com", "먼저"));
        assertThatThrownBy(() -> users.register(new UserRegisterRequest("dup@shop.com", "나중")))
                .isInstanceOf(DuplicateEmailException.class);
        written();
        assertThat(users.get(first.id()).name()).isEqualTo("먼저");
        assertThatThrownBy(() -> users.get(first.id() + 100)).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- 상품

    @Test
    @DisplayName("상품 검색: 동적 조건(prefix·가격 범위)과 정렬·페이징, 조건이 없으면 전체")
    void itemSearch() {
        List.of(new ItemCreateRequest("펜 A", 1_000, 10), new ItemCreateRequest("펜 B", 3_000, 10),
                new ItemCreateRequest("펜 C", 5_000, 10), new ItemCreateRequest("펜 D", 7_000, 10),
                new ItemCreateRequest("노트", 3_000, 10), new ItemCreateRequest("50% 할인", 100, 10),
                new ItemCreateRequest("500원 펜", 500, 10)).forEach(items::create);
        written();

        PageResponse<ItemResponse> page = items.search(new ItemSearchCondition("펜", 2_000L, 7_000L), PageRequest.of(0, 2, Sort.by("price")));
        assertThat(page.content()).extracting(ItemResponse::name).containsExactly("펜 B", "펜 C");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);

        assertThat(items.search(new ItemSearchCondition("50%", null, null), PageRequest.of(0, 10)).content())
                .describedAs("%는 와일드카드가 아니라 문자로 검색된다").extracting(ItemResponse::name).containsExactly("50% 할인");
        assertThat(items.search(new ItemSearchCondition(null, null, null), PageRequest.of(0, 20)).totalElements()).isEqualTo(7);
        assertThat(items.search(new ItemSearchCondition(null, null, null), PageRequest.of(0, 20)).content())
                .describedAs("기본 정렬 id desc").extracting(ItemResponse::id).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("허용되지 않은 정렬 키는 SQL을 보내기 전에 400")
    void invalidSort() {
        SqlRecorder.Recorded<Object> recorded = SqlRecorder.record(() -> {
            assertThatThrownBy(() -> items.search(new ItemSearchCondition(null, null, null), PageRequest.of(0, 10, Sort.by("stock"))))
                    .isInstanceOf(InvalidSortPropertyException.class);
            return null;
        });
        assertThat(recorded.sql()).isEmpty();
    }

    @Test
    @DisplayName("상품 수정(낙관적 락): version이 맞으면 반영되고 응답에 새 version, 옛 version이면 409")
    void itemOptimisticLock() {
        ItemResponse created = items.create(new ItemCreateRequest("펜", 1_000, 10));
        assertThat(created.version()).isZero();

        ItemResponse updated = items.update(created.id(), new ItemUpdateRequest("볼펜", 1_500, 0));
        assertThat(updated.version()).describedAs("응답에는 수정 후 version (JPA는 flush 전 값을 돌려주기 쉬움, 보고서 코드 E)").isEqualTo(1);
        assertThat(updated.name()).isEqualTo("볼펜");

        assertThatThrownBy(() -> items.update(created.id(), new ItemUpdateRequest("만년필", 9_000, 0)))
                .isInstanceOf(OptimisticLockConflictException.class);
        assertThatThrownBy(() -> items.update(created.id() + 100, new ItemUpdateRequest("x", 1, 0)))
                .isInstanceOf(NotFoundException.class);
        written();
        assertThat(items.get(created.id())).isEqualTo(updated);
    }

    // ---------------------------------------------------------------- 주문

    @Test
    @DisplayName("주문: 같은 상품 줄은 합치고, 재고를 줄이고, 상품 version이 오르고, 응답은 조회 결과와 같다")
    void placeOrder() {
        UserResponse user = users.register(new UserRegisterRequest("buyer@shop.com", "구매자"));
        ItemResponse pen = items.create(new ItemCreateRequest("펜", 1_000, 10));
        ItemResponse note = items.create(new ItemCreateRequest("노트", 3_000, 5));

        OrderResponse order = orders.place(new OrderPlaceRequest(user.id(), List.of(
                new OrderLineRequest(note.id(), 1), new OrderLineRequest(pen.id(), 2), new OrderLineRequest(pen.id(), 1))));

        assertThat(order.userName()).isEqualTo("구매자");
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.totalAmount()).isEqualTo(3 * 1_000L + 3_000L);
        assertThat(order.lines()).extracting(l -> List.of(l.itemId(), (long) l.quantity()))
                .containsExactlyInAnyOrder(List.of(pen.id(), 3L), List.of(note.id(), 1L));
        written();
        assertThat(orders.get(order.orderId())).isEqualTo(order);
        assertThat(items.get(pen.id()).stock()).isEqualTo(7);
        assertThat(items.get(pen.id()).version()).describedAs("재고 차감도 행 변경이므로 version + 1").isEqualTo(1);
    }

    @Test
    @DisplayName("재고 부족(409)·없는 상품(404)·없는 사용자(404)면 아무것도 바뀌지 않는다")
    void placeOrderFailures() {
        UserResponse user = users.register(new UserRegisterRequest("buyer@shop.com", "구매자"));
        ItemResponse pen = items.create(new ItemCreateRequest("펜", 1_000, 10));
        ItemResponse note = items.create(new ItemCreateRequest("노트", 3_000, 1));

        assertThatThrownBy(() -> orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(pen.id(), 2), new OrderLineRequest(note.id(), 2)))))
                .isInstanceOf(InsufficientStockException.class);
        assertThatThrownBy(() -> orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(pen.id(), 1), new OrderLineRequest(999_999, 1)))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> orders.place(new OrderPlaceRequest(999_999, List.of(new OrderLineRequest(pen.id(), 1)))))
                .isInstanceOf(NotFoundException.class);
        written();
        assertThat(items.get(pen.id())).describedAs("펜 재고 차감이 롤백되어야 함").isEqualTo(pen);
        assertThat(orders.search(new OrderSearchCondition(null, null), PageRequest.of(0, 10)).totalElements()).isZero();
    }

    @Test
    @DisplayName("재고 50개에 100건이 동시에 주문하면 정확히 50건만 성공하고 재고는 0 (FOR UPDATE)")
    void concurrentOrders() {
        UserResponse user = users.register(new UserRegisterRequest("crowd@shop.com", "군중"));
        ItemResponse item = items.create(new ItemCreateRequest("한정판", 1_000, 50));

        List<Throwable> failures = runConcurrently(100, () -> orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(item.id(), 1)))));

        assertThat(100 - failures.size()).isEqualTo(50);
        assertThat(failures).allMatch(e -> e instanceof InsufficientStockException);
        written();
        assertThat(items.get(item.id()).stock()).isZero();
        assertThat(orders.search(new OrderSearchCondition(user.id(), null), PageRequest.of(0, 1)).totalElements()).isEqualTo(50);
    }

    @Test
    @DisplayName("취소: 재고가 돌아오고 두 번째 취소는 409. 같은 주문을 동시에 10번 취소해도 1번만 성공")
    void cancel() {
        UserResponse user = users.register(new UserRegisterRequest("c@shop.com", "취소"));
        ItemResponse item = items.create(new ItemCreateRequest("펜", 1_000, 10));
        OrderResponse first = orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(item.id(), 3))));

        OrderResponse canceled = orders.cancel(first.orderId());
        assertThat(canceled.status()).isEqualTo(OrderStatus.CANCELED);
        assertThatThrownBy(() -> orders.cancel(first.orderId())).isInstanceOf(InvalidOrderStateException.class);

        OrderResponse second = orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(item.id(), 4))));
        List<Throwable> failures = runConcurrently(10, () -> orders.cancel(second.orderId()));
        assertThat(10 - failures.size()).isEqualTo(1);
        assertThat(failures).allMatch(e -> e instanceof InvalidOrderStateException);
        written();
        assertThat(items.get(item.id()).stock()).isEqualTo(10);
        assertThat(orders.get(first.orderId())).isEqualTo(canceled);
    }

    @Test
    @DisplayName("주문 검색: 조건·정렬·페이징, 사용자 이름(N:1)과 주문상품(1:N) 조립. SQL 수는 페이지 크기와 무관")
    void orderSearch() {
        UserResponse alice = users.register(new UserRegisterRequest("alice@shop.com", "앨리스"));
        UserResponse bob = users.register(new UserRegisterRequest("bob@shop.com", "밥"));
        ItemResponse pen = items.create(new ItemCreateRequest("펜", 1_000, 1_000));
        ItemResponse note = items.create(new ItemCreateRequest("노트", 3_000, 1_000));
        List<OrderResponse> aliceOrders = IntStream.rangeClosed(1, 6)
                .mapToObj(i -> orders.place(new OrderPlaceRequest(alice.id(), List.of(new OrderLineRequest(pen.id(), i), new OrderLineRequest(note.id(), 1)))))
                .toList();
        orders.place(new OrderPlaceRequest(bob.id(), List.of(new OrderLineRequest(pen.id(), 1))));
        orders.cancel(aliceOrders.getFirst().orderId());
        written();

        OrderSearchCondition condition = new OrderSearchCondition(alice.id(), OrderStatus.PLACED);
        SqlRecorder.Recorded<PageResponse<OrderResponse>> small =
                SqlRecorder.record(() -> orders.search(condition, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "totalAmount"))));
        SqlRecorder.Recorded<PageResponse<OrderResponse>> large =
                SqlRecorder.record(() -> orders.search(condition, PageRequest.of(0, 4, Sort.by(Sort.Direction.DESC, "totalAmount"))));

        assertThat(small.result().totalElements()).isEqualTo(5);
        assertThat(small.result().content()).extracting(OrderResponse::totalAmount).containsExactly(9_000L, 8_000L);
        assertThat(large.result().content()).hasSize(4).allMatch(o -> o.userName().equals("앨리스") && o.lines().size() == 2);
        assertThat(large.result().content().getFirst().lines()).describedAs("order_items.id 순")
                .extracting(l -> l.itemName()).containsExactly("펜", "노트");
        assertThat(large.sql()).describedAs("N+1이면 페이지가 커질수록 SQL이 는다").hasSameSizeAs(small.sql());
    }

    @Test
    @DisplayName("사용자별 커서 목록: 겹침·누락 없이 끝까지, id 내림차순")
    void cursor() {
        UserResponse user = users.register(new UserRegisterRequest("cursor@shop.com", "커서"));
        ItemResponse item = items.create(new ItemCreateRequest("펜", 1_000, 100));
        List<Long> placed = IntStream.rangeClosed(1, 7)
                .mapToObj(i -> orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(item.id(), 1)))).orderId())
                .toList();
        written();

        List<Long> seen = new ArrayList<>();
        Long cursor = null;
        CursorResponse<OrderResponse> page;
        do {
            page = orders.listByUser(user.id(), cursor, 3);
            page.content().forEach(o -> seen.add(o.orderId()));
            cursor = page.nextCursor();
        } while (page.hasNext());
        assertThat(seen).isEqualTo(placed.stream().sorted(Comparator.reverseOrder()).toList());
    }

    @Test
    @DisplayName("베스트셀러: [from, to+1일) 반개구간이라 다음날 0시 주문은 빠지고, 취소 주문도 빠진다")
    void bestSellers() {
        UserResponse user = users.register(new UserRegisterRequest("best@shop.com", "집계"));
        ItemResponse pen = items.create(new ItemCreateRequest("펜", 1_000, 100));
        ItemResponse note = items.create(new ItemCreateRequest("노트", 3_000, 100));
        order(user, pen.id(), 2, "2026-10-01T00:00:00");
        order(user, pen.id(), 3, "2026-10-31T23:59:59.999999");
        order(user, note.id(), 4, "2026-10-15T12:00:00");
        order(user, note.id(), 10, "2026-11-01T00:00:00");                   // 다음날 0시: 빠져야 함
        orders.cancel(order(user, note.id(), 10, "2026-10-20T10:00:00"));     // 취소: 빠져야 함
        written();

        List<BestSellerResponse> result = items.bestSellers(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), 10);
        assertThat(result).extracting(b -> List.of(b.itemName(), b.soldQuantity(), b.salesAmount()))
                .containsExactly(List.of("펜", 5L, 5_000L), List.of("노트", 4L, 12_000L));
    }

    // ---------------------------------------------------------------- HTTP

    @Test
    @DisplayName("HTTP: 같은 API 경로·같은 JSON·같은 오류 응답(ProblemDetail)")
    void http() throws Exception {
        long userId = postForId("/api/users", "{\"email\":\"http@shop.com\",\"name\":\"에이치\"}", "id");
        long itemId = postForId("/api/items", "{\"name\":\"펜\",\"price\":1000,\"stock\":1}", "id");
        String orderBody = "{\"userId\":" + userId + ",\"lines\":[{\"itemId\":" + itemId + ",\"quantity\":1}]}";
        long orderId = postForId("/api/orders", orderBody, "orderId");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("재고가 부족합니다 (itemId=" + itemId + ", 요청=1, 재고=0)"));
        mockMvc.perform(get("/api/items?sort=stock,asc")).andExpect(status().isBadRequest());
        written();
        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("에이치"))
                .andExpect(jsonPath("$.lines[0].amount").value(1000));
        mockMvc.perform(get("/api/db/routing"))
                .andExpect(jsonPath("$.write.serverId").value(1))
                .andExpect(jsonPath("$.readOnly.serverId").value(2));
    }

    // ---------------------------------------------------------------- SQL 비교 리포트

    @Test
    @DisplayName("같은 요청에 각 구현이 실제로 보낸 SQL을 build/sql-report/ 에 남긴다")
    void sqlReport() throws IOException {
        StringBuilder report = new StringBuilder("# " + IMPLEMENTATION + "\n");

        UserResponse user = capture(report, "사용자 가입", () -> users.register(new UserRegisterRequest("sql@shop.com", "에스큐엘")));
        ItemResponse pen = capture(report, "상품 생성", () -> items.create(new ItemCreateRequest("펜", 1_000, 100)));
        ItemResponse note = items.create(new ItemCreateRequest("노트", 3_000, 100));
        capture(report, "상품 수정 (낙관적 락)", () -> items.update(pen.id(), new ItemUpdateRequest("볼펜", 1_200, 0)));
        OrderResponse order = capture(report, "주문 (상품 2종)",
                () -> orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(pen.id(), 2), new OrderLineRequest(note.id(), 1)))));
        for (int i = 0; i < 3; i++) {
            orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(pen.id(), 1), new OrderLineRequest(note.id(), 1))));
        }
        capture(report, "주문 취소", () -> orders.cancel(order.orderId()));
        written();
        capture(report, "상품 검색 (조건 3개, 2페이지)", () -> items.search(new ItemSearchCondition("펜", 500L, 5_000L), PageRequest.of(1, 1, Sort.by("price"))));
        capture(report, "주문 단건 조회", () -> orders.get(order.orderId()));
        capture(report, "주문 검색 (4건 페이지)", () -> orders.search(new OrderSearchCondition(user.id(), null), PageRequest.of(0, 4)));
        capture(report, "사용자별 커서 (3건)", () -> orders.listByUser(user.id(), null, 3));
        capture(report, "베스트셀러", () -> items.bestSellers(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 10));

        File dir = new File("build/sql-report");
        dir.mkdirs();
        Files.writeString(new File(dir, IMPLEMENTATION + ".md").toPath(), report.toString());
        System.out.println(report);
    }

    // ---------------------------------------------------------------- helpers

    /** 쓰기 다음 레플리카 조회 전에 복제를 기다린다 */
    private void written() {
        ReplicatedMySql.awaitReplica();
    }

    private <T> T capture(StringBuilder report, String title, Supplier<T> block) {
        SqlRecorder.Recorded<T> recorded = SqlRecorder.record(block);
        report.append("\n## ").append(title).append(" — SQL ").append(recorded.sql().size()).append("개\n");
        recorded.sql().forEach(sql -> report.append(sql).append('\n'));
        return recorded.result();
    }

    private long order(UserResponse user, long itemId, int quantity, String orderedAt) {
        long id = orders.place(new OrderPlaceRequest(user.id(), List.of(new OrderLineRequest(itemId, quantity)))).orderId();
        ReplicatedMySql.executeOnSource("UPDATE orders SET ordered_at = ? WHERE id = ?", LocalDateTime.parse(orderedAt), id);
        return id;
    }

    private long postForId(String url, String body, String field) throws Exception {
        String json = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) throw new IllegalStateException(json);
        return Long.parseLong(m.group(1));
    }

    /** 동시에 실행하고 실패한 예외 목록을 돌려준다 */
    private List<Throwable> runConcurrently(int count, Callable<?> task) {
        CountDownLatch ready = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            List<Future<Throwable>> futures = IntStream.range(0, count).mapToObj(i -> executor.submit(() -> {
                ready.await();
                try {
                    task.call();
                    return (Throwable) null;
                } catch (Exception e) {
                    return (Throwable) e;
                }
            })).toList();
            ready.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> f : futures) {
                Throwable t = f.get();
                if (t != null) failures.add(t);
            }
            return failures;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
