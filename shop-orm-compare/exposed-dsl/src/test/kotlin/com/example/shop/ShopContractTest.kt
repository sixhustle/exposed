package com.example.shop

import com.example.shop.routing.RoutingService
import com.example.shop.common.error.DuplicateEmailException
import com.example.shop.common.error.InsufficientStockException
import com.example.shop.common.error.InvalidOrderStateException
import com.example.shop.common.error.InvalidSortPropertyException
import com.example.shop.common.error.NotFoundException
import com.example.shop.common.error.OptimisticLockConflictException
import com.example.shop.item.ItemCreateRequest
import com.example.shop.item.ItemSearchCondition
import com.example.shop.item.ItemUpdateRequest
import com.example.shop.item.ItemService
import com.example.shop.order.OrderLineRequest
import com.example.shop.order.OrderPlaceRequest
import com.example.shop.order.OrderSearchCondition
import com.example.shop.order.OrderStatus
import com.example.shop.order.OrderService
import com.example.shop.user.UserRegisterRequest
import com.example.shop.user.UserService
import com.example.shop.support.ReplicatedMySql
import com.example.shop.support.SqlRecorder
import com.example.shop.support.SqlRecordingPostProcessor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import com.example.shop.item.Items
import com.example.shop.order.OrderItems
import com.example.shop.order.Orders
import com.example.shop.user.Users
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val IMPLEMENTATION = "exposed-dsl"

/**
 * 세 프로젝트(jpa-querydsl, exposed-dao, exposed-dsl)가 각자의 언어로 같은 시나리오를 갖는 계약 테스트.
 * 테스트 이름·순서·검증 값을 세 프로젝트에서 똑같이 유지한다 (JPA는 Java로 같은 내용).
 *
 * 규칙: 쓰기 다음 readOnly 조회(레플리카) 전에는 [written]으로 복제를 기다린다. 실제 서비스라면 사용자가 겪을 레플리카 지연이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ShopContractTest.SqlRecordingConfig::class)
class ShopContractTest {

	@Autowired lateinit var users: UserService
	@Autowired lateinit var items: ItemService
	@Autowired lateinit var orders: OrderService
	@Autowired lateinit var routing: RoutingService
	@Autowired lateinit var mockMvc: MockMvc
	@Autowired lateinit var transactionManager: PlatformTransactionManager

	@TestConfiguration(proxyBeanMethods = false)
	class SqlRecordingConfig {
		@Bean
		fun sqlRecordingPostProcessor() = SqlRecordingPostProcessor()
	}

	@BeforeEach
	fun cleanDatabase() = ReplicatedMySql.truncateAll()

	// ---------------------------------------------------------------- 라우팅

	@Test
	@DisplayName("쓰기 트랜잭션은 소스(server_id=1, shop), readOnly 트랜잭션은 레플리카(server_id=2, shop_reader)로 간다")
	fun routing() {
		val write = routing.inWriteTransaction()
		val read = routing.inReadOnlyTransaction()

		assertThat(write.serverId).isEqualTo(1)
		assertThat(write.user).startsWith("shop@")
		assertThat(write.readOnly).isFalse()
		assertThat(read.serverId).isEqualTo(2)
		assertThat(read.user).startsWith("shop_reader@")
		assertThat(read.readOnly).isTrue()
	}

	@Test
	@DisplayName("소스에 쓴 데이터가 레플리카로 복제되어 readOnly 조회에서 보인다")
	fun replication() {
		val user = users.register(UserRegisterRequest("repl@shop.com", "복제"))
		written()
		assertThat(users.get(user.id)).isEqualTo(user)
	}

	// ---------------------------------------------------------------- 사용자

	@Test
	@DisplayName("같은 이메일로 두 번 가입하면 409, 먼저 가입한 사용자는 그대로")
	fun duplicateEmail() {
		val first = users.register(UserRegisterRequest("dup@shop.com", "먼저"))
		assertThatThrownBy { users.register(UserRegisterRequest("dup@shop.com", "나중")) }
			.isInstanceOf(DuplicateEmailException::class.java)
		written()
		assertThat(users.get(first.id).name).isEqualTo("먼저")
		assertThatThrownBy { users.get(first.id + 100) }.isInstanceOf(NotFoundException::class.java)
	}

	// ---------------------------------------------------------------- 상품

	@Test
	@DisplayName("상품 검색: 동적 조건(prefix·가격 범위)과 정렬·페이징, 조건이 없으면 전체")
	fun itemSearch() {
		listOf("펜 A" to 1_000L, "펜 B" to 3_000L, "펜 C" to 5_000L, "펜 D" to 7_000L, "노트" to 3_000L, "50% 할인" to 100L, "500원 펜" to 500L)
			.forEach { (name, price) -> items.create(ItemCreateRequest(name, price, 10)) }
		written()

		val page = items.search(ItemSearchCondition(keyword = "펜", minPrice = 2_000, maxPrice = 7_000), PageRequest.of(0, 2, Sort.by("price")))
		assertThat(page.content.map { it.name }).containsExactly("펜 B", "펜 C")
		assertThat(page.totalElements).isEqualTo(3)
		assertThat(page.totalPages).isEqualTo(2)

		assertThat(items.search(ItemSearchCondition(keyword = "50%"), PageRequest.of(0, 10)).content.map { it.name })
			.describedAs("%는 와일드카드가 아니라 문자로 검색된다").containsExactly("50% 할인")
		assertThat(items.search(ItemSearchCondition(), PageRequest.of(0, 20)).totalElements).isEqualTo(7)
		assertThat(items.search(ItemSearchCondition(), PageRequest.of(0, 20)).content.map { it.id })
			.describedAs("기본 정렬 id desc").isSortedAccordingTo(compareByDescending { it })
	}

	@Test
	@DisplayName("허용되지 않은 정렬 키는 SQL을 보내기 전에 400")
	fun invalidSort() {
		val (_, sql) = SqlRecorder.record {
			assertThatThrownBy { items.search(ItemSearchCondition(), PageRequest.of(0, 10, Sort.by("stock"))) }
				.isInstanceOf(InvalidSortPropertyException::class.java)
		}
		assertThat(sql).isEmpty()
	}

	@Test
	@DisplayName("상품 수정(낙관적 락): version이 맞으면 반영되고 응답에 새 version, 옛 version이면 409")
	fun itemOptimisticLock() {
		val created = items.create(ItemCreateRequest("펜", 1_000, 10))
		assertThat(created.version).isZero()

		val updated = items.update(created.id, ItemUpdateRequest("볼펜", 1_500, version = 0))
		assertThat(updated.version).describedAs("응답에는 수정 후 version (JPA는 flush 전 값을 돌려주기 쉬움, 보고서 코드 E)").isEqualTo(1)
		assertThat(updated.name).isEqualTo("볼펜")

		assertThatThrownBy { items.update(created.id, ItemUpdateRequest("만년필", 9_000, version = 0)) }
			.isInstanceOf(OptimisticLockConflictException::class.java)
		assertThatThrownBy { items.update(created.id + 100, ItemUpdateRequest("x", 1, version = 0)) }
			.isInstanceOf(NotFoundException::class.java)
		written()
		assertThat(items.get(created.id)).isEqualTo(updated)
	}

	// ---------------------------------------------------------------- 주문

	@Test
	@DisplayName("주문: 같은 상품 줄은 합치고, 재고를 줄이고, 상품 version이 오르고, 응답은 조회 결과와 같다")
	fun placeOrder() {
		val user = users.register(UserRegisterRequest("buyer@shop.com", "구매자"))
		val pen = items.create(ItemCreateRequest("펜", 1_000, 10))
		val note = items.create(ItemCreateRequest("노트", 3_000, 5))

		val order = orders.place(OrderPlaceRequest(user.id, listOf(
			OrderLineRequest(note.id, 1), OrderLineRequest(pen.id, 2), OrderLineRequest(pen.id, 1),
		)))

		assertThat(order.userName).isEqualTo("구매자")
		assertThat(order.status).isEqualTo(OrderStatus.PLACED)
		assertThat(order.totalAmount).isEqualTo(3 * 1_000L + 3_000L)
		assertThat(order.lines.map { it.itemId to it.quantity }).containsExactlyInAnyOrder(pen.id to 3, note.id to 1)
		written()
		assertThat(orders.get(order.orderId)).isEqualTo(order)
		assertThat(items.get(pen.id).stock).isEqualTo(7)
		assertThat(items.get(pen.id).version).describedAs("재고 차감도 행 변경이므로 version + 1").isEqualTo(1)
	}

	@Test
	@DisplayName("재고 부족(409)·없는 상품(404)·없는 사용자(404)면 아무것도 바뀌지 않는다")
	fun placeOrderFailures() {
		val user = users.register(UserRegisterRequest("buyer@shop.com", "구매자"))
		val pen = items.create(ItemCreateRequest("펜", 1_000, 10))
		val note = items.create(ItemCreateRequest("노트", 3_000, 1))

		assertThatThrownBy { orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(pen.id, 2), OrderLineRequest(note.id, 2)))) }
			.isInstanceOf(InsufficientStockException::class.java)
		assertThatThrownBy { orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(pen.id, 1), OrderLineRequest(999_999, 1)))) }
			.isInstanceOf(NotFoundException::class.java)
		assertThatThrownBy { orders.place(OrderPlaceRequest(999_999, listOf(OrderLineRequest(pen.id, 1)))) }
			.isInstanceOf(NotFoundException::class.java)
		written()
		assertThat(items.get(pen.id)).describedAs("펜 재고 차감이 롤백되어야 함").isEqualTo(pen)
		assertThat(orders.search(OrderSearchCondition(), PageRequest.of(0, 10)).totalElements).isZero()
	}

	@Test
	@DisplayName("재고 50개에 100건이 동시에 주문하면 정확히 50건만 성공하고 재고는 0 (FOR UPDATE)")
	fun concurrentOrders() {
		val user = users.register(UserRegisterRequest("crowd@shop.com", "군중"))
		val item = items.create(ItemCreateRequest("한정판", 1_000, 50))

		val results = runConcurrently(100) { runCatching { orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(item.id, 1)))) } }

		assertThat(results.count { it.isSuccess }).isEqualTo(50)
		assertThat(results.mapNotNull { it.exceptionOrNull() }).allMatch { it is InsufficientStockException }
		written()
		assertThat(items.get(item.id).stock).isZero()
		assertThat(orders.search(OrderSearchCondition(userId = user.id), PageRequest.of(0, 1)).totalElements).isEqualTo(50)
	}

	@Test
	@DisplayName("취소: 재고가 돌아오고 두 번째 취소는 409. 같은 주문을 동시에 10번 취소해도 1번만 성공")
	fun cancel() {
		val user = users.register(UserRegisterRequest("c@shop.com", "취소"))
		val item = items.create(ItemCreateRequest("펜", 1_000, 10))
		val first = orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(item.id, 3))))

		val canceled = orders.cancel(first.orderId)
		assertThat(canceled.status).isEqualTo(OrderStatus.CANCELED)
		assertThatThrownBy { orders.cancel(first.orderId) }.isInstanceOf(InvalidOrderStateException::class.java)

		val second = orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(item.id, 4))))
		val results = runConcurrently(10) { runCatching { orders.cancel(second.orderId) } }
		assertThat(results.count { it.isSuccess }).isEqualTo(1)
		assertThat(results.mapNotNull { it.exceptionOrNull() }).allMatch { it is InvalidOrderStateException }
		written()
		assertThat(items.get(item.id).stock).isEqualTo(10)
		assertThat(orders.get(first.orderId)).isEqualTo(canceled)
	}

	@Test
	@DisplayName("주문 검색: 조건·정렬·페이징, 사용자 이름(N:1)과 주문상품(1:N) 조립. SQL 수는 페이지 크기와 무관")
	fun orderSearch() {
		val alice = users.register(UserRegisterRequest("alice@shop.com", "앨리스"))
		val bob = users.register(UserRegisterRequest("bob@shop.com", "밥"))
		val pen = items.create(ItemCreateRequest("펜", 1_000, 1_000))
		val note = items.create(ItemCreateRequest("노트", 3_000, 1_000))
		val aliceOrders = (1..6).map { i -> orders.place(OrderPlaceRequest(alice.id, listOf(OrderLineRequest(pen.id, i), OrderLineRequest(note.id, 1)))) }
		orders.place(OrderPlaceRequest(bob.id, listOf(OrderLineRequest(pen.id, 1))))
		orders.cancel(aliceOrders[0].orderId)
		written()

		val condition = OrderSearchCondition(userId = alice.id, status = OrderStatus.PLACED)
		val (small, smallSql) = SqlRecorder.record { orders.search(condition, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "totalAmount"))) }
		val (large, largeSql) = SqlRecorder.record { orders.search(condition, PageRequest.of(0, 4, Sort.by(Sort.Direction.DESC, "totalAmount"))) }

		assertThat(small.totalElements).isEqualTo(5)
		assertThat(small.content.map { it.totalAmount }).containsExactly(9_000L, 8_000L)
		assertThat(large.content).hasSize(4).allMatch { it.userName == "앨리스" && it.lines.size == 2 }
		assertThat(large.content.first().lines.map { it.itemName }).describedAs("order_items.id 순").containsExactly("펜", "노트")
		assertThat(largeSql).describedAs("N+1이면 페이지가 커질수록 SQL이 는다").hasSameSizeAs(smallSql)
	}

	@Test
	@DisplayName("사용자별 커서 목록: 겹침·누락 없이 끝까지, id 내림차순")
	fun cursor() {
		val user = users.register(UserRegisterRequest("cursor@shop.com", "커서"))
		val item = items.create(ItemCreateRequest("펜", 1_000, 100))
		val placed = (1..7).map { orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(item.id, 1)))).orderId }
		written()

		val seen = mutableListOf<Long>()
		var cursor: Long? = null
		do {
			val page = orders.listByUser(user.id, cursor, 3)
			seen += page.content.map { it.orderId }
			cursor = page.nextCursor
		} while (page.hasNext)
		assertThat(seen).isEqualTo(placed.sortedDescending())
	}

	@Test
	@DisplayName("베스트셀러: [from, to+1일) 반개구간이라 다음날 0시 주문은 빠지고, 취소 주문도 빠진다")
	fun bestSellers() {
		val user = users.register(UserRegisterRequest("best@shop.com", "집계"))
		val pen = items.create(ItemCreateRequest("펜", 1_000, 100))
		val note = items.create(ItemCreateRequest("노트", 3_000, 100))
		fun order(item: Long, qty: Int, at: String): Long {
			val id = orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(item, qty)))).orderId
			setOrderedAt(id, LocalDateTime.parse(at))
			return id
		}
		order(pen.id, 2, "2026-10-01T00:00:00")
		order(pen.id, 3, "2026-10-31T23:59:59.999999")
		order(note.id, 4, "2026-10-15T12:00:00")
		order(note.id, 10, "2026-11-01T00:00:00")                      // 다음날 0시: 빠져야 함
		orders.cancel(order(note.id, 10, "2026-10-20T10:00:00"))        // 취소: 빠져야 함
		written()

		val result = items.bestSellers(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), 10)
		assertThat(result.map { Triple(it.itemName, it.soldQuantity, it.salesAmount) })
			.containsExactly(Triple("펜", 5L, 5_000L), Triple("노트", 4L, 12_000L))
	}

	// ---------------------------------------------------------------- 스키마

	@Test
	@DisplayName("매핑이 Flyway 스키마와 일치한다")
	fun schemaMatchesFlyway() {
		// JPA의 ddl-auto=validate 대신: Exposed 테이블 정의와 실제 스키마의 차이(필요한 DDL)가 없어야 한다
		val diff = TransactionTemplate(transactionManager).execute {
			MigrationUtils.statementsRequiredForDatabaseMigration(Users, Items, Orders, OrderItems, withLogs = false)
		}
		assertThat(diff).describedAs("필요한 DDL:\n%s", diff.joinToString("\n")).isEmpty()
	}

	// ---------------------------------------------------------------- HTTP

	@Test
	@DisplayName("HTTP: 같은 API 경로·같은 JSON·같은 오류 응답(ProblemDetail)")
	fun http() {
		val userId = postForId("/api/users", """{"email":"http@shop.com","name":"에이치"}""", "id")
		val itemId = postForId("/api/items", """{"name":"펜","price":1000,"stock":1}""", "id")
		val orderId = postForId("/api/orders", """{"userId":$userId,"lines":[{"itemId":$itemId,"quantity":1}]}""", "orderId")

		mockMvc.post("/api/orders") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"userId":$userId,"lines":[{"itemId":$itemId,"quantity":1}]}"""
		}.andExpect {
			status { isConflict() }
			jsonPath("$.detail") { value("재고가 부족합니다 (itemId=$itemId, 요청=1, 재고=0)") }
		}
		mockMvc.get("/api/items?sort=stock,asc").andExpect { status { isBadRequest() } }
		written()
		mockMvc.get("/api/orders/$orderId").andExpect {
			status { isOk() }
			jsonPath("$.userName") { value("에이치") }
			jsonPath("$.lines[0].amount") { value(1000) }
		}
		mockMvc.get("/api/db/routing").andExpect {
			jsonPath("$.write.serverId") { value(1) }
			jsonPath("$.readOnly.serverId") { value(2) }
		}
	}

	// ---------------------------------------------------------------- SQL 비교 리포트

	@Test
	@DisplayName("같은 요청에 각 구현이 실제로 보낸 SQL을 build/sql-report/ 에 남긴다")
	fun sqlReport() {
		val report = StringBuilder("# $IMPLEMENTATION\n")
		fun <T> capture(title: String, block: () -> T): T {
			val (result, sql) = SqlRecorder.record(block)
			report.append("\n## $title — SQL ${sql.size}개\n")
			sql.forEach { report.append(it).append('\n') }
			return result
		}

		val user = capture("사용자 가입") { users.register(UserRegisterRequest("sql@shop.com", "에스큐엘")) }
		val pen = capture("상품 생성") { items.create(ItemCreateRequest("펜", 1_000, 100)) }
		val note = items.create(ItemCreateRequest("노트", 3_000, 100))
		capture("상품 수정 (낙관적 락)") { items.update(pen.id, ItemUpdateRequest("볼펜", 1_200, 0)) }
		val order = capture("주문 (상품 2종)") { orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(pen.id, 2), OrderLineRequest(note.id, 1)))) }
		repeat(3) { orders.place(OrderPlaceRequest(user.id, listOf(OrderLineRequest(pen.id, 1), OrderLineRequest(note.id, 1)))) }
		capture("주문 취소") { orders.cancel(order.orderId) }
		written()
		capture("상품 검색 (조건 3개, 2페이지)") { items.search(ItemSearchCondition("펜", 500, 5_000), PageRequest.of(1, 1, Sort.by("price"))) }
		capture("주문 단건 조회") { orders.get(order.orderId) }
		capture("주문 검색 (4건 페이지)") { orders.search(OrderSearchCondition(userId = user.id), PageRequest.of(0, 4)) }
		capture("사용자별 커서 (3건)") { orders.listByUser(user.id, null, 3) }
		capture("베스트셀러") { items.bestSellers(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 10) }

		val dir = File("build/sql-report").apply { mkdirs() }
		File(dir, "$IMPLEMENTATION.md").writeText(report.toString())
		println(report)
	}

	// ---------------------------------------------------------------- helpers

	/** 쓰기 다음 레플리카 조회 전에 복제를 기다린다 */
	private fun written() = ReplicatedMySql.awaitReplica()

	private fun setOrderedAt(orderId: Long, at: LocalDateTime) {
		DriverManager.getConnection(ReplicatedMySql.source.jdbcUrl, "root", "shop").use { c ->
			c.prepareStatement("UPDATE orders SET ordered_at = ? WHERE id = ?").use { ps ->
				ps.setObject(1, at)
				ps.setLong(2, orderId)
				ps.executeUpdate()
			}
		}
	}

	private fun postForId(url: String, body: String, field: String): Long {
		val json = mockMvc.post(url) {
			contentType = MediaType.APPLICATION_JSON
			content = body
		}.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return Regex("\"$field\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toLong()
	}

	private fun <T> runConcurrently(count: Int, task: () -> T): List<T> {
		val ready = CountDownLatch(1)
		Executors.newFixedThreadPool(16).use { executor ->
			val futures = (0 until count).map { executor.submit(Callable { ready.await(); task() }) }
			ready.countDown()
			return futures.map { it.get() }
		}
	}

	companion object {
		@JvmStatic
		@DynamicPropertySource
		fun mysql(registry: DynamicPropertyRegistry) {
			registry.add("app.datasource.writer.jdbc-url") { ReplicatedMySql.writerJdbcUrl() }
			registry.add("app.datasource.writer.username") { "shop" }
			registry.add("app.datasource.writer.password") { "shop" }
			registry.add("app.datasource.reader.jdbc-url") { ReplicatedMySql.readerJdbcUrl() }
			registry.add("app.datasource.reader.username") { ReplicatedMySql.READER_USER }
			registry.add("app.datasource.reader.password") { ReplicatedMySql.READER_PASSWORD }
		}
	}
}
