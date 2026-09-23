# 로컬 쿠버네티스에서 직접 띄워 보기 (kind)

가이드 21-19의 실측을 재현하는 순서. Docker만 있으면 된다.

```bash
# 1. 도구 (없으면)
brew install kind kubernetes-cli helm      # 또는 각 프로젝트의 바이너리 다운로드

# 2. 1노드 클러스터
kind create cluster --name kt-demo --image kindest/node:v1.31.0

# 3. 앱 이미지 빌드 → 클러스터에 적재
cd ../library-api && ./gradlew bootJar -x test
docker build -t library-api:demo .
kind load docker-image library-api:demo --name kt-demo

# 4. 클러스터 안에 MySQL + Secret (데모용, 운영은 매니지드 DB 권장)
kubectl create secret generic library-api-secrets \
  --from-literal=SPRING_DATASOURCE_USERNAME=library \
  --from-literal=SPRING_DATASOURCE_PASSWORD=library
kubectl create deployment mysql --image=mysql:8.4
kubectl set env deployment/mysql MYSQL_ROOT_PASSWORD=root MYSQL_DATABASE=library \
  MYSQL_USER=library MYSQL_PASSWORD=library
kubectl expose deployment mysql --port=3306

# 5. 배포
cd ../deploy
helm upgrade --install lib helm/library-api \
  -f helm/library-api/values.yaml -f helm/library-api/values-kind-demo.yaml --wait

# 6. 호출
kubectl port-forward svc/lib-library-api 18080:80 &
curl -X POST localhost:18080/api/authors -H 'Content-Type: application/json' -d '{"name":"김코틀"}'
curl 'localhost:18080/api/books?page=0&size=5'
```

## 실험해 볼 것

```bash
# ① 파드 수 × 커넥션 풀 = DB 커넥션  (가이드 21B-1)
kubectl exec deploy/mysql -- mysql -uroot -proot -N -B \
  -e "select variable_value from performance_schema.global_status where variable_name='Threads_connected'"
kubectl scale deployment lib-library-api --replicas=4   # 다시 재 보면 약 2배

# ② readiness 실패 + maxUnavailable:0  (가이드 21-12)
kubectl set env deployment/lib-library-api SPRING_DATASOURCE_URL="jdbc:mysql://nowhere:3306/library"
kubectl get pods                       # 새 파드 0/1, 기존 파드는 그대로 Running
kubectl get endpointslice -l kubernetes.io/service-name=lib-library-api -o yaml | grep ready
curl localhost:18080/api/books         # 그래도 200
kubectl rollout undo deployment/lib-library-api

# ③ OOMKilled  (가이드 21-14)
kubectl set resources deployment/lib-library-api --limits=memory=200Mi --requests=memory=200Mi
kubectl describe pod -l app.kubernetes.io/instance=lib | grep -A2 "Last State"
kubectl set resources deployment/lib-library-api --limits=memory=768Mi --requests=memory=768Mi

# 정리
kind delete cluster --name kt-demo
```
