package com.tiaoma.repository;

import com.tiaoma.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Like.LikeId> {

    /** ที่เที่ยวโปรดของผู้ใช้ เรียงจากบันทึกล่าสุดก่อน (id.userId = ฟิลด์ userId ใน LikeId) */
    List<Like> findByIdUserIdOrderByCreatedAtDesc(Long userId);
}
