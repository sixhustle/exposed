package com.example.shop.common.exposed

import com.example.shop.common.ShopClock
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.EntityBatchUpdate
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * @LastModifiedDate 대응: flush 직전에 바뀐 컬럼이 있으면 updatedAt을 끼워 넣는다 (보고서 코드 A).
 * DAO: setter는 SQL을 내지 않고 writeValues에 쌓였다가 그 테이블을 읽기 직전·flushCache()·커밋 때 UPDATE로 나간다 (보고서 09).
 */
abstract class AuditedLongEntity(id: EntityID<Long>, private val auditTable: AuditableLongIdTable) : LongEntity(id) {
	var createdAt by auditTable.createdAt
	var updatedAt by auditTable.updatedAt

	override fun flush(batch: EntityBatchUpdate?): Boolean {
		if (writeValues.isNotEmpty() && writeValues.keys.none { it == auditTable.updatedAt }) updatedAt = ShopClock.now()
		return super.flush(batch)
	}
}

/**
 * 미뤄 둔 INSERT/UPDATE를 지금 보낸다. 커밋 때 난 예외는 @Repository 번역 경로 밖이라 TransactionSystemException으로 감싸진다
 * (보고서 09 측정). 그래서 Repository의 쓰기 메서드 끝에서 호출해 예외를 번역 경로 안에서 터뜨린다.
 */
fun flushChanges() {
	TransactionManager.current().flushCache()
}
