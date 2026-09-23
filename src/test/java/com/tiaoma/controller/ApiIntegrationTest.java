package com.tiaoma.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiaoma.model.User;
import com.tiaoma.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** เทสต์ระดับ API: สิทธิ์การเข้าถึง, การสมัคร/ล็อกอิน, รีวิว และการจัดการบัญชี */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository userRepository;

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    // ---------- สาธารณะ ----------

    @Test
    @DisplayName("GET /api/places เปิดให้ทุกคนอ่านได้ และมีฟิลด์สถิติแนบมาด้วย")
    void places_arePublic() throws Exception {
        mvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].ratingAverage").exists())
                .andExpect(jsonPath("$[0].likeCount").exists());
    }

    @Test
    @DisplayName("GET /api/articles ไม่ส่งเนื้อหาเต็ม (content) มาในหน้ารายการ")
    void articleList_omitsContent() throws Exception {
        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].readMinutes").exists())
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/articles/{id} ส่งเนื้อหาเต็มและบทความที่เกี่ยวข้อง")
    void articleDetail_includesContent() throws Exception {
        MvcResult list = mvc.perform(get("/api/articles")).andReturn();
        Integer id = com.jayway.jsonpath.JsonPath.read(list.getResponse().getContentAsString(), "$[0].id");

        mvc.perform(get("/api/articles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.related").isArray());
    }

    @Test
    @DisplayName("ขอบทความที่ไม่มีอยู่ ต้องได้ 404 พร้อมข้อความภาษาไทย")
    void articleDetail_notFound() throws Exception {
        mvc.perform(get("/api/articles/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ไม่พบบทความนี้"));
    }

    // ---------- สิทธิ์การเข้าถึง ----------

    @Test
    @DisplayName("ยังไม่ล็อกอิน เรียก /api/likes ต้องได้ 401")
    void likes_requireLogin() throws Exception {
        mvc.perform(get("/api/likes")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ยังไม่ล็อกอิน เรียก /api/account ต้องได้ 401")
    void account_requiresLogin() throws Exception {
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ยังไม่ล็อกอิน เรียก /api/admin/users ต้องได้ 401")
    void admin_requiresLogin() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("สมาชิกธรรมดาเรียก /api/admin/users ต้องได้ 403 ไม่ใช่ 200")
    void admin_forbiddenForNormalUser() throws Exception {
        MockHttpSession session = registerAndLogin("normal@example.com", "password123");

        mvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "เฉพาะผู้ดูแลระบบ (ADMIN) เท่านั้นที่เข้าใช้งานส่วนนี้ได้"));
    }

    // ---------- สมัคร / ล็อกอิน ----------

    @Test
    @DisplayName("สมัครด้วยรหัสผ่านอ่อน (สั้นหรือไม่มีตัวเลข) ต้องถูกปฏิเสธ")
    void register_weakPassword_rejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "ทดสอบ", "email", "weak@example.com", "password", "abc"))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "ทดสอบ", "email", "weak2@example.com", "password", "onlyletters"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ล็อกอินผิด ต้องได้ 401 และข้อความต้องไม่บอกว่าอีเมลมีในระบบหรือไม่")
    void login_wrongPassword_genericMessage() throws Exception {
        registerUser("login@example.com", "password123");

        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "login@example.com", "password", "wrongpass123"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String msg = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(msg).contains("อีเมลหรือรหัสผ่านไม่ถูกต้อง");
    }

    @Test
    @DisplayName("forgot-password ตอบข้อความเดียวกันเสมอ ไม่ว่าอีเมลจะมีจริงหรือไม่ (กันการเดาบัญชี)")
    void forgotPassword_sameResponseAlways() throws Exception {
        registerUser("exists@example.com", "password123");

        MvcResult a = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "exists@example.com"))))
                .andExpect(status().isOk()).andReturn();

        MvcResult b = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "nobody-here@example.com"))))
                .andExpect(status().isOk()).andReturn();

        assertThat(a.getResponse().getContentAsString())
                .isEqualTo(b.getResponse().getContentAsString());
    }

    // ---------- ที่เที่ยวโปรด ----------

    @Test
    @DisplayName("บันทึกที่เที่ยวโปรดแล้วต้องอยู่ในรายการ และกดซ้ำได้โดยผลไม่เปลี่ยน")
    void likes_addIsIdempotent() throws Exception {
        MockHttpSession session = registerAndLogin("liker@example.com", "password123");
        String placeId = firstPlaceId();

        mvc.perform(put("/api/likes/" + placeId).session(session)).andExpect(status().isNoContent());
        mvc.perform(put("/api/likes/" + placeId).session(session)).andExpect(status().isNoContent());

        mvc.perform(get("/api/likes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(placeId));

        mvc.perform(delete("/api/likes/" + placeId).session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/likes").session(session))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- รีวิว ----------

    @Test
    @DisplayName("อ่านรีวิวได้โดยไม่ต้องล็อกอิน แต่เขียนต้องล็อกอินก่อน")
    void reviews_readPublicWriteProtected() throws Exception {
        String placeId = firstPlaceId();

        mvc.perform(get("/api/places/" + placeId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());

        mvc.perform(put("/api/places/" + placeId + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("rating", 5, "comment", "ดีมาก"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("เขียนรีวิวซ้ำของผู้ใช้เดิม = แก้ไขของเดิม ไม่ใช่เพิ่มใหม่")
    void reviews_upsertNotDuplicate() throws Exception {
        MockHttpSession session = registerAndLogin("reviewer@example.com", "password123");
        String placeId = firstPlaceId();

        mvc.perform(put("/api/places/" + placeId + "/reviews").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("rating", 4, "comment", "ดี"))))
                .andExpect(status().isOk());

        mvc.perform(put("/api/places/" + placeId + "/reviews").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("rating", 5, "comment", "ดีมากกว่าที่คิด"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/places/" + placeId + "/reviews").session(session))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.average").value(5.0))
                .andExpect(jsonPath("$.items[0].comment").value("ดีมากกว่าที่คิด"));
    }

    @Test
    @DisplayName("ให้ดาวนอกช่วง 1-5 ต้องถูกปฏิเสธ")
    void reviews_ratingOutOfRange_rejected() throws Exception {
        MockHttpSession session = registerAndLogin("badrating@example.com", "password123");
        String placeId = firstPlaceId();

        mvc.perform(put("/api/places/" + placeId + "/reviews").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("rating", 9, "comment", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ลบรีวิวของคนอื่นไม่ได้ (ถ้าไม่ใช่แอดมิน)")
    void reviews_cannotDeleteOthers() throws Exception {
        MockHttpSession author = registerAndLogin("author@example.com", "password123");
        String placeId = firstPlaceId();

        MvcResult created = mvc.perform(put("/api/places/" + placeId + "/reviews").session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("rating", 5, "comment", "ของฉัน"))))
                .andReturn();
        Integer reviewId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        MockHttpSession other = registerAndLogin("other@example.com", "password123");
        mvc.perform(delete("/api/places/" + placeId + "/reviews/" + reviewId).session(other))
                .andExpect(status().isForbidden());
    }

    // ---------- จัดการบัญชี ----------

    @Test
    @DisplayName("เปลี่ยนรหัสผ่านต้องยืนยันรหัสเดิมให้ถูกก่อน")
    void changePassword_requiresCurrentPassword() throws Exception {
        MockHttpSession session = registerAndLogin("changepw@example.com", "password123");

        mvc.perform(post("/api/account/change-password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "wrongone123", "newPassword", "newpassword456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("รหัสผ่านปัจจุบันไม่ถูกต้อง"));
    }

    @Test
    @DisplayName("เปลี่ยนรหัสผ่านสำเร็จแล้วรหัสใหม่ใช้ล็อกอินได้ รหัสเก่าใช้ไม่ได้")
    void changePassword_success() throws Exception {
        MockHttpSession session = registerAndLogin("pwok@example.com", "password123");

        mvc.perform(post("/api/account/change-password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "password123", "newPassword", "newpassword456"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "pwok@example.com", "password", "password123"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "pwok@example.com", "password", "newpassword456"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("แก้ไขชื่อที่แสดงได้")
    void updateProfileName() throws Exception {
        MockHttpSession session = registerAndLogin("rename@example.com", "password123");

        mvc.perform(put("/api/account/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "ชื่อใหม่ ทดสอบ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ชื่อใหม่ ทดสอบ"));

        mvc.perform(get("/api/account").session(session))
                .andExpect(jsonPath("$.name").value("ชื่อใหม่ ทดสอบ"));
    }

    @Test
    @DisplayName("ลบบัญชีต้องกรอกรหัสผ่านให้ถูก และเมื่อลบแล้วผู้ใช้ต้องหายจากระบบ")
    void deleteAccount() throws Exception {
        MockHttpSession session = registerAndLogin("bye@example.com", "password123");

        mvc.perform(delete("/api/account").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", "wrongpass123"))))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/account").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", "password123"))))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail("bye@example.com")).isEmpty();
    }

    // ---------- ไฟล์ static / PWA / sitemap ----------

    @Test
    @DisplayName("sitemap.xml สร้างจากฐานข้อมูลและมี URL ของทุกสถานที่")
    void sitemap_listsPlaces() throws Exception {
        String xml = mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(xml).contains("<urlset");
        assertThat(xml).contains("/places.html");
        assertThat(xml).contains("/map.html");
        assertThat(xml).contains("/place-detail.html?id=");
        assertThat(xml).contains("/article-detail.html?id=");
        // ต้องไม่ escape ซ้ำจนกลายเป็น &amp;amp;
        assertThat(xml).doesNotContain("&amp;amp;");
    }

    @Test
    @DisplayName("sw.js ต้องห้าม cache ไม่งั้นผู้ใช้ติด service worker ตัวเก่า")
    void serviceWorker_isNotCached() throws Exception {
        String cacheControl = mvc.perform(get("/sw.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Cache-Control");

        assertThat(cacheControl).isNotNull();
        assertThat(cacheControl).contains("no-cache");
    }

    @Test
    @DisplayName("หน้าเว็บหลักเปิดได้โดยไม่ต้องล็อกอิน")
    void staticPages_areReachable() throws Exception {
        for (String page : new String[]{"/index.html", "/places.html", "/map.html",
                                        "/articles.html", "/about.html", "/404.html"}) {
            mvc.perform(get(page)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("robots.txt ห้าม index หน้าส่วนตัว")
    void robots_disallowsPrivatePages() throws Exception {
        String robots = mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(robots).contains("Disallow: /admin.html");
        assertThat(robots).contains("Disallow: /api/");
    }

    // ---------- ตัวช่วย ----------

    private void registerUser(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "ผู้ใช้ทดสอบ", "email", email, "password", password))))
                .andExpect(status().isCreated());
    }

    private MockHttpSession registerAndLogin(String email, String password) throws Exception {
        registerUser(email, password);
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String firstPlaceId() throws Exception {
        MvcResult result = mvc.perform(get("/api/places")).andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$[0].id");
    }

    @SuppressWarnings("unused")
    private User asAdmin(String email) {
        User u = userRepository.findByEmail(email).orElseThrow();
        u.setRole(User.Role.ADMIN);
        return userRepository.save(u);
    }
}
