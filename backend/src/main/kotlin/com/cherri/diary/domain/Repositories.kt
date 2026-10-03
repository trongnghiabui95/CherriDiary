package com.cherri.diary.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun lockById(id: Long): User?
}

interface CategoryRepository : JpaRepository<Category, Long>

interface ProductRepository : JpaRepository<Product, Long> {
    fun findByShortCode(shortCode: String): Product?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.shortCode = :shortCode")
    fun lockByShortCode(shortCode: String): Product?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    fun lockById(id: Long): Product?
}

interface CustomerRepository : JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    fun findByPhoneNumber(phoneNumber: String): Customer?
    fun findByTiktokId(tiktokId: String): Customer?
    fun findByFacebookId(facebookId: String): Customer?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    fun lockById(id: Long): Customer?
}

interface OrderRepository : JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    fun findByUserIdAndRequestId(userId: Long, requestId: UUID): Order?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    fun lockById(id: Long): Order?
}

interface LiveSessionRepository : JpaRepository<LiveSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LiveSession s where s.id = :id")
    fun lockById(id: Long): LiveSession?
}
