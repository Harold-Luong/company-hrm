# Cấu hình asymmetric JWT (RS256)

Access token và refresh token dùng hai cặp RSA riêng biệt. Auth service ký bằng private key,
xác thực bằng public key và chỉ chấp nhận RS256. Các claim, thời hạn token và cơ chế refresh
session được giữ nguyên. Xem [JJWT 0.12.6](https://github.com/jwtk/jjwt/blob/0.12.6/README.adoc#signed-jwts).

## Tạo khóa cho local

Chạy từ thư mục module, một lần trước khi khởi động ứng dụng:

```bash
mkdir -p keys
chmod 700 keys
(
  umask 077
  for purpose in access refresh; do
    if [ -e "keys/$purpose-private.pem" ] || [ -e "keys/$purpose-public.pem" ]; then
      echo "Khóa $purpose đã tồn tại; giữ nguyên để tiếp tục xác thực token cũ." >&2
      exit 1
    fi
  done
  for purpose in access refresh; do
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "keys/$purpose-private.pem" || exit 1
    openssl pkey -in "keys/$purpose-private.pem" -pubout -out "keys/$purpose-public.pem" || exit 1
  done
)
./mvnw spring-boot:run
```

Private key phải là PEM PKCS#8 (`BEGIN PRIVATE KEY`); public key là PEM X.509
(`BEGIN PUBLIC KEY`). Khóa phải có ít nhất 2048 bit. Ứng dụng nạp khóa một lần khi khởi động
và báo lỗi nếu thiếu/sai định dạng, hai khóa không khớp, hoặc access/refresh dùng chung cặp khóa.
Thư mục `keys/` đã được bỏ qua trong Git. Khóa trong `src/test/resources/jwt/` chỉ dành cho test,
không dùng để chạy ứng dụng thực tế.

## Cấu hình môi trường

Mặc định ứng dụng đọc bốn file trong `./keys/` theo working directory. Khi triển khai,
mount khóa từ secret storage và đặt các biến sau với đường dẫn thực tế:

```bash
export JWT_ACCESS_PRIVATE_KEY=file:/run/secrets/jwt/access-private.pem
export JWT_ACCESS_PUBLIC_KEY=file:/run/secrets/jwt/access-public.pem
export JWT_REFRESH_PRIVATE_KEY=file:/run/secrets/jwt/refresh-private.pem
export JWT_REFRESH_PUBLIC_KEY=file:/run/secrets/jwt/refresh-public.pem
```

Chỉ auth service giữ private key. Service nghiệp vụ nhận **access public key**, xác minh chữ ký
RS256, `iss=auth-service`, `aud=hrm-api-access` và thời hạn `exp` trước khi tin các claim.
Refresh token chỉ được xử lý tại auth service, với `aud=hrm-api-refresh` và `type=refresh`.
Hiện phân phối public key qua file cấu hình; chưa có JWKS endpoint hay cơ chế nhiều khóa/`kid`.

## Chuyển đổi và thay khóa

`JWT_SECRET_ACCESS` và `JWT_SECRET_REFRESH` không còn được sử dụng. Token HMAC cũ sẽ bị từ chối
sau khi chuyển cấu hình; người dùng cần đăng nhập lại. Không cần đổi schema database.
Giữ khóa ổn định giữa các lần restart và đồng bộ chúng giữa các instance auth service.
Thay cặp khóa làm token tương ứng đã phát hành mất hiệu lực; cần restart để nạp khóa mới,
đồng thời cập nhật access public key tại các service xác thực token.

Chạy `./mvnw test` để kiểm tra RS256, public-key verification, từ chối chữ ký/thuật toán/claim sai,
cấu hình khóa không hợp lệ và các luồng API hiện có.
