package com.example.shop.routing

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

private const val IMPLEMENTATION = "exposed-dao"

/** 어느 MySQL 서버·계정으로 붙었는지. 소스 server_id=1, 레플리카 server_id=2 (docker/mysql 의 my.cnf) */
data class ConnectionInfo(val serverId: Int, val user: String, val readOnly: Boolean)

data class RoutingResponse(val implementation: String, val write: ConnectionInfo, val readOnly: ConnectionInfo)

@Service
class RoutingService {

	@Transactional
	fun inWriteTransaction(): ConnectionInfo = query()

	@Transactional(readOnly = true)
	fun inReadOnlyTransaction(): ConnectionInfo = query()

	private fun query(): ConnectionInfo =
		TransactionManager.current().exec("SELECT @@server_id, CURRENT_USER(), @@read_only") { rs ->
			rs.next()
			ConnectionInfo(rs.getInt(1), rs.getString(2), rs.getBoolean(3))
		}!!
}

@RestController
class RoutingController(private val routingService: RoutingService) {

	// 쓰기는 server 1(shop), 읽기는 server 2(shop_reader)
	@GetMapping("/api/db/routing")
	fun routing() = RoutingResponse(IMPLEMENTATION, routingService.inWriteTransaction(), routingService.inReadOnlyTransaction())
}
