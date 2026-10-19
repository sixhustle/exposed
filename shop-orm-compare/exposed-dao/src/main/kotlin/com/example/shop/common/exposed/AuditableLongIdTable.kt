package com.example.shop.common.exposed

import com.example.shop.common.ShopClock
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.ReplaceStatement
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * JPA의 BaseTimeEntity + @EnableJpaAuditing 에 해당한다 (보고서 코드 A, 과제 4).
 *
 * - INSERT: clientDefault로 created_at·updated_at을 채운다
 * - UPDATE: 멤버 update를 ERROR로 막아 updateAudited만 쓰게 한다 (멤버가 확장 함수보다 우선)
 * - upsert: 기본 동작이 충돌 시 created_at까지 덮어쓴다 (보고서 08-4 측정) → 막고 upsertAudited 제공
 * - replace: MySQL REPLACE = DELETE + INSERT → 감사 컬럼 초기화, FK 충돌 가능 → 막는다
 * 타입을 Table로 바꿔 부르는 우회는 컴파일러가 막지 못한다. 리뷰에서 잡는다.
 */
abstract class AuditableLongIdTable(name: String) : LongIdTable(name) {
	val createdAt = datetime("created_at").clientDefault { ShopClock.now() }
	val updatedAt = datetime("updated_at").clientDefault { ShopClock.now() }

	@Deprecated(UPDATE_FORBIDDEN, ReplaceWith("updateAudited(where, body)"), DeprecationLevel.ERROR)
	@Suppress("UNUSED_PARAMETER")
	fun update(where: () -> Op<Boolean>, limit: Int? = null, body: AuditableLongIdTable.(UpdateStatement) -> Unit): Int =
		error(UPDATE_FORBIDDEN)

	@Deprecated(UPDATE_FORBIDDEN, ReplaceWith("updateAudited({ Op.TRUE }, body)"), DeprecationLevel.ERROR)
	@Suppress("UNUSED_PARAMETER")
	fun update(limit: Int? = null, body: AuditableLongIdTable.(UpdateStatement) -> Unit): Int =
		error(UPDATE_FORBIDDEN)

	@Deprecated("감사 컬럼이 있는 테이블은 upsertAudited를 쓰세요 (기본 upsert는 created_at을 덮어씀)", level = DeprecationLevel.ERROR)
	@Suppress("UNUSED_PARAMETER")
	fun upsert(
		vararg keys: Column<*>,
		onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
		onUpdateExclude: List<Column<*>>? = null,
		where: (() -> Op<Boolean>)? = null,
		body: AuditableLongIdTable.(UpsertStatement<Long>) -> Unit,
	): UpsertStatement<Long> = error("forbidden")

	@Deprecated("감사 컬럼이 있는 테이블에 REPLACE(DELETE + INSERT)를 쓰지 마세요", level = DeprecationLevel.ERROR)
	@Suppress("UNUSED_PARAMETER")
	fun replace(body: AuditableLongIdTable.(UpdateBuilder<*>) -> Unit): ReplaceStatement<Long> = error("forbidden")

	private companion object {
		const val UPDATE_FORBIDDEN = "감사 컬럼(updated_at)이 있는 테이블은 update 대신 updateAudited를 쓰세요"
	}
}

/** update와 같지만 updated_at을 함께 갱신한다 */
fun <T : AuditableLongIdTable> T.updateAudited(where: () -> Op<Boolean>, body: T.(UpdateStatement) -> Unit): Int {
	val table: Table = this
	val self = this
	return table.update(where) {
		it[updatedAt] = ShopClock.now()
		self.body(it)
	}
}

/** 새 행이면 created_at·updated_at을 채우고, 충돌로 UPDATE가 되면 created_at은 두고 updated_at만 갱신 */
fun <T : AuditableLongIdTable> T.upsertAudited(vararg keys: Column<*>, body: T.(UpsertStatement<Long>) -> Unit): UpsertStatement<Long> {
	val table: Table = this
	val self = this
	return table.upsert(*keys, onUpdateExclude = listOf(createdAt)) { self.body(it) }
}
