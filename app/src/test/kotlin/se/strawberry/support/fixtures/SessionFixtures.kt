package se.strawberry.support.fixtures

import se.strawberry.repository.session.SessionRepository

object SessionFixtures {

    fun createActiveSession(id: String = "session-${System.nanoTime()}"): SessionRepository.Session {
        return SessionRepository.Session(
            id = id,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000,
            status = SessionRepository.Session.Status.ACTIVE
        )
    }

    fun createClosedSession(id: String = "session-${System.nanoTime()}"): SessionRepository.Session {
        return SessionRepository.Session(
            id = id,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000,
            status = SessionRepository.Session.Status.CLOSED
        )
    }
}
