# ingress-nginx 어노테이션 치트시트 (Ingress 리소스에 붙인다)

| 어노테이션 | 뜻 | 예시 값 |
|---|---|---|
| `nginx.ingress.kubernetes.io/proxy-body-size` | 업로드 최대 크기 | `8m` |
| `nginx.ingress.kubernetes.io/proxy-read-timeout` | 응답 대기 시간(초) | `30` |
| `nginx.ingress.kubernetes.io/proxy-connect-timeout` | 연결 대기 시간(초) | `5` |
| `nginx.ingress.kubernetes.io/limit-rps` | IP당 초당 요청 제한 | `100` |
| `nginx.ingress.kubernetes.io/enable-cors` + `cors-allow-origin` | CORS 허용 | `true`, `https://app.example.com` |
| `nginx.ingress.kubernetes.io/ssl-redirect` | HTTP → HTTPS 리다이렉트 | `true` |
| `nginx.ingress.kubernetes.io/rewrite-target` | 경로 재작성 | `/$2` (경로에 정규식 사용 시) |
| `nginx.ingress.kubernetes.io/canary` + `canary-weight` | 카나리 배포(비율) | `true`, `10` |
| `nginx.ingress.kubernetes.io/whitelist-source-range` | 허용 IP 대역 | `10.0.0.0/8` |
| `cert-manager.io/cluster-issuer` | TLS 인증서 자동 발급 | `letsencrypt-prod` |

## 자주 겪는 문제
- **502 Bad Gateway**: 파드가 준비되지 않았거나(readinessProbe), 앱 포트와 Service targetPort 불일치.
- **504 Gateway Timeout**: 앱 응답이 `proxy-read-timeout`보다 느림. 타임아웃을 늘리기 전에 쿼리를 먼저 본다.
- **413 Request Entity Too Large**: `proxy-body-size` 기본값(1m)보다 큰 업로드.
- **클라이언트 IP가 모두 같게 보임**: `use-forwarded-headers`, `externalTrafficPolicy: Local` 확인.
- **무중단 배포인데 502가 뜸**: 파드 종료 시 `preStop` 대기 + `terminationGracePeriodSeconds`로 LB에서 빠질 시간을 준다.
