package com.resolveiq.notification.inbound;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboundEmailRepository extends JpaRepository<InboundEmail, Long> {

    List<InboundEmail> findAllByOrderByReceivedAtDescIdDesc(Pageable page);
}
