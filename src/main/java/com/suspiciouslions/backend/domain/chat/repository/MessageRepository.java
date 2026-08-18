package com.suspiciouslions.backend.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.suspiciouslions.backend.domain.chat.entity.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

	Optional<Message> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);

	List<Message> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Pageable pageable);

	List<Message> findByChatRoomIdOrderBySentAtDescIdDesc(Long chatRoomId, Pageable pageable);

	List<Message> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long beforeMessageId,
			Pageable pageable);

	List<Message> findByChatRoomIdAndIdGreaterThanOrderByIdAsc(Long chatRoomId, Long afterMessageId,
			Pageable pageable);
}
