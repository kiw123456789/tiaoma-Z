# Build แล้วรันใน image เดียว — เหมาะกับขึ้น Railway/Render หรือรัน local ผ่าน docker compose
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# ก๊อปเฉพาะ pom.xml ก่อน เพื่อให้ Docker cache ชั้น dependency ได้
COPY pom.xml .
RUN mvn -B dependency:go-offline -q
COPY src ./src
RUN mvn -B clean package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/tiaoma-backend-1.0.0.jar app.jar
EXPOSE 8080
# โฟลเดอร์ uploads เค็บนอกรูป image ไว้ให้เหลือตอนรัน (ไฟล์อัปโหลดไม่หายตอน build ใหม่)
RUN mkdir -p /app/uploads
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
