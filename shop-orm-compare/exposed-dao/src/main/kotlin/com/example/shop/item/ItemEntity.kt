package com.example.shop.item

import com.example.shop.common.error.InsufficientStockException
import com.example.shop.common.exposed.AuditedLongEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntityClass

class ItemEntity(id: EntityID<Long>) : AuditedLongEntity(id, Items) {
	companion object : LongEntityClass<ItemEntity>(Items)

	var name by Items.name
	var price by Items.price
	var stock by Items.stock
	var version by Items.version // @Version처럼 자동 증가하지 않는다. 바꿀 때 직접 +1

	fun decreaseStock(quantity: Int) {
		if (stock < quantity) throw InsufficientStockException(id.value, quantity, stock)
		stock -= quantity
		version += 1
	}

	fun changeInfo(name: String, price: Long) {
		this.name = name
		this.price = price
		version += 1
	}

	fun toResponse() = ItemResponse(id.value, name, price, stock, version)
}
