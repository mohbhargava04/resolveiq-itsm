package com.resolveiq.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(Long recipientId, ChannelType channel,
                                                                      Pageable pageable);

    long countByRecipientIdAndChannelAndReadFalse(Long recipientId, ChannelType channel);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipient.id = :recipientId and n.read = false")
    int markAllRead(Long recipientId);

    @Query("select n from Notification n join fetch n.recipient order by n.createdAt desc")
    List<Notification> findRecentWithRecipient(Pageable pageable);

    @Query("select n from Notification n join fetch n.recipient where n.channel = :channel order by n.createdAt desc")
    List<Notification> findRecentByChannel(ChannelType channel, Pageable pageable);
}
