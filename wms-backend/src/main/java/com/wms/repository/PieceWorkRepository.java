package com.wms.repository;
import com.wms.entity.PieceWork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.LocalDateTime;
import java.util.List;
public interface PieceWorkRepository extends JpaRepository<PieceWork, Long>, JpaSpecificationExecutor<PieceWork> {
    List<PieceWork> findByWorkerName(String workerName);
    List<PieceWork> findByWorkDateBetween(LocalDateTime start, LocalDateTime end);
    List<PieceWork> findByProductName(String productName);
}
