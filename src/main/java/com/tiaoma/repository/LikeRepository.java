package com.tiaoma.repository;

import com.tiaoma.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Like.LikeId> {

    /** ที่เที่ยวโปรดของผู้ใช้ เรียงจากบันทึกล่าสุดก่อน (id.userId = ฟิลด์ userId ใน LikeId) */
    List<Like> findByIdUserIdOrderByCreatedAtDesc(Long userId);

    long countByIdUserId(Long userId);

    long countByIdPlaceId(String placeId);

    void deleteByIdUserId(Long userId);

    void deleteByIdPlaceId(String placeId);

    /** จำนวนคนกดถูกใจของทุกสถานที่ในคิวรี่เดียว (ใช้จัดอันดับ "ยอดนิยม")
     *  หมายเหตุ: entity ชื่อ UserLike ไม่ใช่ Like เพราะ LIKE เป็นคีย์เวิร์ดของ JPQL */
    @Query("select l.id.placeId, count(l) from UserLike l group by l.id.placeId")
    List<Object[]> countGroupedByPlace();
}
