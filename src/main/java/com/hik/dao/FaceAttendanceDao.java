package com.hik.dao;

import com.hik.entity.FaceAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaceAttendanceDao extends JpaRepository<FaceAttendance, Long> {

        List<FaceAttendance> findByDatetimeBetween(String startTime,String endTime);

}
