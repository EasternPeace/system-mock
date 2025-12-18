package se.strawberry.support.fakes

import se.strawberry.repository.session.SessionRepository

class FakeSessionRepository : SessionRepository {
    val sessions = mutableMapOf<String, SessionRepository.Session>()

    override fun create(session: SessionRepository.Session): Boolean {
        sessions[session.id] = session
        return true
    }

    override fun get(id: String): SessionRepository.Session? {
        return sessions[id]
    }

    override fun close(id: String): Boolean {
        val s = sessions[id] ?: return false
        sessions[id] = s.copy(status = SessionRepository.Session.Status.CLOSED)
        return true
    }
}
