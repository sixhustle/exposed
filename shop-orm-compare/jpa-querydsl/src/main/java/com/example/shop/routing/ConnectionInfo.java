package com.example.shop.routing;

/** 어느 MySQL 서버·계정으로 붙었는지. 소스 server_id=1, 레플리카 server_id=2 (docker/mysql 의 my.cnf) */
public record ConnectionInfo(int serverId, String user, boolean readOnly) {
}
