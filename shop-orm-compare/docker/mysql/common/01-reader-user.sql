-- 소스·레플리카 양쪽에서 실행한다 (복제로 넘기지 않고 각자 만든다. 소스는 99-reset-binlog.sql 에서 초기화 이력을 비운다).
-- 애플리케이션의 reader 풀은 레플리카에 이 계정으로 붙는다. SELECT 권한만 있어 라우팅이 틀리면 쓰기가 즉시 실패한다.
CREATE USER IF NOT EXISTS 'shop_reader'@'%' IDENTIFIED BY 'shop_reader';
GRANT SELECT ON shop.* TO 'shop_reader'@'%';
