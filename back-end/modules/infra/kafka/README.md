# Kafka local cho HRM

Kafka local và hai service đã có luồng Employee gửi `EmployeeAccountRequested`,
Auth tạo Account rồi trả `AccountCreated` / `AccountCreationFailed`.
Xem [migration, cách bật và ví dụ API](ACCOUNT-PROVISIONING.md).
Luồng tạo tài khoản chờ kích hoạt đã có; API đặt mật khẩu/kích hoạt chưa triển khai.

## 1. Khởi chạy

Cần Docker Engine và Docker Compose v2 trở lên. Chạy các lệnh sau từ thư mục
`back-end/modules`:

```bash
docker compose -f infra/kafka/compose.yaml up -d
docker compose -f infra/kafka/compose.yaml ps -a
docker compose -f infra/kafka/compose.yaml logs kafka-init
```

Kết quả đúng: `kafka` ở trạng thái `healthy`, `kafka-init` kết thúc với `Exited (0)`.
Init chỉ chạy tạo topic, nên kết thúc là bình thường. Lệnh `up -d` có thể trả về
trước khi init hoàn tất; kiểm tra trạng thái này trước khi chạy smoke test.
Có thể chạy lại init bằng `docker compose -f infra/kafka/compose.yaml run --rm kafka-init`.
Topic đã tồn tại sẽ được giữ nguyên; init không sửa cấu hình topic cũ.

| Thành phần | Cấu hình local |
|---|---|
| Image | `apache/kafka:4.1.2` |
| Chế độ | KRaft, một node broker/controller, không cần ZooKeeper |
| Service chạy trên máy host/IDE | `localhost:9092` |
| Client cùng Docker network `hrm-kafka_default` | `kafka:19092` |
| Lưu dữ liệu | Named volume `hrm-kafka_kafka-data` |
| Topic | Một partition, replication factor 1, retention 7 ngày |

Cổng host chỉ bind `127.0.0.1`. Nếu sau này chạy service trong container, phải nối
vào cùng network và đặt `KAFKA_BOOTSTRAP_SERVERS=kafka:19092`; `localhost` trong
container trỏ về chính container đó.

## 2. Topic và service

| Topic | Message | Producer | Consumer group |
|---|---|---|---|
| `hrm.employee.account-requests.v1` | `EmployeeAccountRequested` | Employee | Auth: `auth-account-requests-v1` |
| `hrm.auth.account-results.v1` | `AccountCreated`, `AccountCreationFailed` | Auth | Employee: `employee-account-results-v1` |
| `hrm.employee.lifecycle.v1` | Dành cho mở rộng vòng đời nhân viên | Chưa dùng | Chưa có |
| `hrm.auth.account-lifecycle.v1` | Dành cho mở rộng trạng thái tài khoản | Chưa dùng | Chưa có |
| `hrm.local.smoke.v1` | Message thử kết nối | Smoke test | Group thử riêng |

Hai service có `spring-boot-starter-kafka` và cấu hình `application.yaml`:

- `KAFKA_BOOTSTRAP_SERVERS`: mặc định `localhost:9092`.
- `HRM_EVENTS_ENABLED`: mặc định `false`; bật sau migration để worker/listener chạy.
- Listener dùng `hrm.events.request-group` / `result-group` riêng, không dùng một
  group chung giữa Auth và Employee; topic cũng cấu hình qua `hrm.events.*`.
- Producer String key/value, `acks=all`, idempotence, giới hạn thời gian chờ gửi.
- Consumer tắt auto commit, đọc `earliest` khi chưa có offset, ack từng record sau
  khi transaction nghiệp vụ hoàn tất. Hiện lỗi được retry vô hạn, chưa có DLT.
- Broker/consumer tắt tự tạo topic. Outbox giữ sự kiện đã commit và retry khi broker lỗi.

Flow cấp tài khoản dùng `EmployeeAccountRequested`. Không gửi payload thử vào
topic nghiệp vụ. Idempotence producer không thay
thế Outbox hoặc chống trùng event/request ở consumer.

## 3. Kiểm tra kết nối từ cả hai service

Cần JDK 21. Các test dưới đây khởi tạo riêng phần Kafka của Spring bằng cấu hình
thật trong `src/main/resources/application.yaml`, không khởi tạo controller,
database hoặc JWT. Mỗi service gửi rồi đọc lại message với marker riêng trên topic
smoke; không ghi dữ liệu thử vào topic nghiệp vụ.

```bash
(cd auth-service-main && KAFKA_SMOKE_TEST=true ./mvnw -Dtest=KafkaConnectionTests test)
(cd employee-service && KAFKA_SMOKE_TEST=true ./mvnw -Dtest=KafkaConnectionTests test)
```

Test này mặc định được bỏ qua khi chạy `./mvnw test`; chỉ bật khi broker local và
topic đã sẵn sàng. Các lần chạy dùng group thử riêng, không commit offset của
consumer nghiệp vụ.

Xem topic:

```bash
docker compose -f infra/kafka/compose.yaml exec -T kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --describe
```

Sau khi chạy smoke test, kiểm tra lưu dữ liệu qua restart:

```bash
docker compose -f infra/kafka/compose.yaml exec -T kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 \
  --topic hrm.local.smoke.v1 --partition 0 --offset earliest \
  --max-messages 1 --timeout-ms 15000

docker compose -f infra/kafka/compose.yaml restart kafka
docker compose -f infra/kafka/compose.yaml up -d --wait kafka

docker compose -f infra/kafka/compose.yaml exec -T kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 \
  --topic hrm.local.smoke.v1 --partition 0 --offset earliest \
  --max-messages 1 --timeout-ms 15000
```

Hai lần phải in cùng message đầu tiên (thực hiện liên tiếp, trước khi hết retention).
Kiểm tra này xác nhận việc lưu dữ liệu qua restart, chưa chứng minh khả năng chịu
lỗi khi mất ổ đĩa hoặc mất máy.

## 4. Dừng và xem log

```bash
docker compose -f infra/kafka/compose.yaml logs --tail 100 kafka
docker compose -f infra/kafka/compose.yaml down
```

`down` giữ volume; chạy lại `up -d` sẽ sử dụng dữ liệu cũ. Không thêm `-v` nếu muốn
giữ dữ liệu: `down -v` xóa volume, toàn bộ message và consumer offset của cụm local.
Không đổi `CLUSTER_ID` khi tái sử dụng volume đã format.

Nếu không kết nối được: kiểm tra broker healthy, init exit code 0, cổng 9092 còn
trống và bootstrap address đúng theo nơi chạy client. Bootstrap thành công chưa
đủ nếu advertised listener trỏ tới địa chỉ client không truy cập được.

## 5. Phạm vi production

Compose này dùng PLAINTEXT và một broker, chỉ dành cho local. Trước production,
thực hiện các phase staging/production trong tài liệu chính: TLS/SASL, ACL theo
service/topic, nhiều broker và quorum phù hợp, replication/ISR, giám sát, backup
và quy trình khôi phục. `acks=all` với một replica vẫn chỉ lưu trên một broker.

Tham khảo cấu hình từ [Apache Kafka Docker](https://kafka.apache.org/41/getting-started/docker/)
và [Spring Boot Kafka](https://docs.spring.io/spring-boot/reference/messaging/kafka.html).
