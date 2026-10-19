package com.example.shop.item

import com.example.shop.common.exposed.AuditableLongIdTable
import com.example.shop.common.exposed.SortableColumns

/** Flyway V1 스키마의 items를 매핑한다 */
object Items : AuditableLongIdTable("items") {
	val name = varchar("name", 100).index("idx_items_name")
	val price = long("price")
	val stock = integer("stock")

	/** 행 버전. JPA @Version과 같은 의미(행이 바뀔 때마다 +1)로 쓰지만 증가는 직접 쓴다 */
	val version = long("version")

	/** 정렬 화이트리스트: id, name, price, createdAt */
	val SORTABLE = SortableColumns(mapOf("id" to id, "name" to name, "price" to price, "createdAt" to createdAt), id)
}
