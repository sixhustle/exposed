-- GTID 자동 위치: 소스의 바이너리 로그 처음(99-reset-binlog.sql 이후)부터 받는다.
-- GET_SOURCE_PUBLIC_KEY=1: caching_sha2_password 계정을 TLS 없이 인증하기 위해 공개키를 받는다.
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST = 'mysql-source',
    SOURCE_PORT = 3306,
    SOURCE_USER = 'repl',
    SOURCE_PASSWORD = 'repl',
    SOURCE_AUTO_POSITION = 1,
    GET_SOURCE_PUBLIC_KEY = 1;
START REPLICA;

-- 레플리카에는 복제 스레드만 쓴다. 관리자(root)의 실수 쓰기까지 막는다.
SET PERSIST read_only = ON;
SET PERSIST super_read_only = ON;
