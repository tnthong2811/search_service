***

# 🔍 Search & AI Extraction Service (Exam Bank)

**Search Service** là một microservice cốt lõi trong hệ thống Exam Bank, đóng vai trò như "bộ não" xử lý dữ liệu và tìm kiếm. Service này chịu trách nhiệm bóc tách nội dung từ các file tài liệu (PDF, Word, Ảnh) do người dùng tải lên, sử dụng trí tuệ nhân tạo (Google Gemini Vision) để tự động nhận diện và trích xuất câu hỏi trắc nghiệm, sau đó lưu trữ vào Elasticsearch để phục vụ tìm kiếm toàn văn bản (Full-text Search).

## 🌟 Chức năng chính (Core Features)

* **Lắng nghe sự kiện (Event-Driven):** Lắng nghe các event từ `exam_service` qua RabbitMQ khi có file đề thi mới được upload.
* **Tải file từ Object Storage:** Tự động kết nối và tải file gốc từ MinIO.
* **Bóc tách văn bản (Text Extraction):** Sử dụng Apache Tika để đọc nội dung từ các định dạng file văn bản chuẩn (PDF, DOCX).
* **Trích xuất thông minh (AI OCR & Parsing):** Tích hợp Google Gemini Vision API để nhận diện chữ trên hình ảnh (OCR) và cấu trúc hóa dữ liệu thô thành danh sách câu hỏi trắc nghiệm định dạng JSON.
* **Phản hồi kết quả (Callback):** Đóng gói kết quả bóc tách và gửi ngược lại `exam_service` qua RabbitMQ.
* **Tìm kiếm toàn văn bản:** (Dự kiến) Lưu trữ và chỉ mục hóa (indexing) câu hỏi vào Elasticsearch để tối ưu hóa tốc độ tìm kiếm.

## 🛠 Công nghệ sử dụng (Tech Stack)

* **Framework:** Spring Boot 4.0.5, Java 21
* **Message Broker:** RabbitMQ (Spring AMQP)
* **Search Engine:** Elasticsearch (Spring Data Elasticsearch)
* **Object Storage:** MinIO Client (v8.5.10)
* **AI Integration:** Google Gemini API
* **Document Parsing:** Apache Tika (v2.9.2)
* **Khác:** Lombok, Spring Validation

## 🏗 Kiến trúc & Luồng xử lý (Architecture Flow)

1.  `exam_service` báo cáo có file mới tải lên qua exchange `exam.events` với routing key `exam.source.uploaded`.
2.  `search_service` bắt được thông báo từ queue `search.events.queue`.
3.  Dùng thư viện `minio` tải file từ bucket `exam-sources`.
4.  Tùy thuộc vào định dạng file:
   * Nếu là Image (PNG, JPG): Gửi thẳng file ảnh qua Google Gemini Vision.
   * Nếu là Document (PDF, DOCX): Dùng Apache Tika đọc text, sau đó gửi text cho Gemini.
5.  Gemini trả về mảng JSON chứa danh sách câu hỏi và đáp án.
6.  `search_service` bọc JSON này vào Event và bắn ngược lại exchange `exam.events` với routing key `search.ai.extracted`.

## ⚙️ Biến môi trường & Cấu hình (Environment Variables)

Service được cấu hình để dễ dàng deploy trên các môi trường khác nhau thông qua biến môi trường. Tham khảo file `application.properties` để biết các giá trị mặc định.

### System & Server
* `SERVER_PORT`: Cổng chạy ứng dụng (Mặc định: `8086`)

### RabbitMQ
* `RABBITMQ_HOST`: Địa chỉ RabbitMQ (Mặc định: `localhost`)
* `RABBITMQ_PORT`: Cổng RabbitMQ (Mặc định: `5672`)
* `RABBITMQ_USERNAME`: Tài khoản (Mặc định: `guest`)
* `RABBITMQ_PASSWORD`: Mật khẩu (Mặc định: `guest`)

### MinIO (Object Storage)
* `MINIO_URL`: Endpoint của MinIO (Mặc định: `http://localhost:9000`)
* `MINIO_ACCESS_KEY`: Access Key (Mặc định: `admin`)
* `MINIO_SECRET_KEY`: Secret Key (Mặc định: `password`)
* `MINIO_BUCKET_NAME`: Tên bucket chứa file đề thi (Mặc định: `exam-sources`)

### Elasticsearch
* `ELASTICSEARCH_URL`: Endpoint của Elasticsearch (Mặc định: `http://localhost:9200`)

### AI (Google Gemini)
* `GEMINI_API_KEY`: API Key để gọi Google Gemini API. **(Bắt buộc phải cấu hình biến này trên môi trường Production để bảo mật)**.

## 🚀 Hướng dẫn khởi chạy (Getting Started)

### Yêu cầu hệ thống (Prerequisites)
Đảm bảo bạn đã cài đặt và cấu hình xong các dịch vụ nền tảng sau:
* Java 21
* Maven
* RabbitMQ
* MinIO
* Elasticsearch

### Các bước chạy Local

1.  **Cấu hình API Key:**
    Thay thế `GEMINI_API_KEY` trong file `application.properties` (hoặc cấu hình qua biến môi trường của hệ điều hành/IDE) bằng API Key thực tế của bạn.

2.  **Build dự án:**
    ```bash
    mvn clean install
    ```

3.  **Chạy ứng dụng:**
    ```bash
    mvn spring-boot:run
    ```
    Hoặc chạy file `SearchServiceApplication.java` trực tiếp trên IDE (IntelliJ/Eclipse).

Ứng dụng sẽ khởi động tại địa chỉ: `http://localhost:8086/api/v1/search`

## 📦 API Endpoints

* Context path mặc định: `/api/v1/search`
  *(Các API phục vụ tìm kiếm thủ công cho Client/Frontend sẽ được cập nhật chi tiết bằng Swagger/OpenAPI)*

