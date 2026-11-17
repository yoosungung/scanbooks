import Foundation
import UIKit

enum SessionStoreError: Error, Equatable {
    case sessionNotFound(String)
    case pageNotFound(String)
    case notPNG
    case invalidJPEG
}

/// Local `sessions/{id}/` store — ARCHITECTURE §1.8 · §1.11 · ios/DESIGN.md.
final class SessionStore: @unchecked Sendable {
    private let root: URL
    private let fm = FileManager.default
    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        return e
    }()
    private let decoder = JSONDecoder()
    private let lock = NSLock()

    /// `root` is the app container (Application Support / filesDir equivalent).
    /// Sessions live under `root/sessions/`.
    init(rootDirectory: URL) {
        self.root = rootDirectory
    }

    static func isPNG(_ data: Data) -> Bool {
        guard data.count >= 8 else { return false }
        let sig: [UInt8] = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]
        return data.prefix(8).elementsEqual(sig)
    }

    static func pngFromJPEG(_ jpeg: Data) throws -> Data {
        guard let image = UIImage(data: jpeg), let png = image.pngData() else {
            throw SessionStoreError.invalidJPEG
        }
        return png
    }

    func sessionDirectory(id: String) -> URL {
        root.appendingPathComponent("sessions", isDirectory: true)
            .appendingPathComponent(id, isDirectory: true)
    }

    func createSession(title: String, id: String = UUID().uuidString, now: Date = Date()) throws -> ScanSession {
        let createdAt = ISO8601DateFormatter().string(from: now)
        let session = ScanSession(
            id: id,
            createdAt: createdAt,
            title: title,
            pageIds: [],
            exportPdfUri: nil
        )
        try ensureDir(sessionDirectory(id: session.id).appendingPathComponent("pages", isDirectory: true))
        try writeSession(session)
        return session
    }

    func loadSession(id: String) throws -> ScanSession {
        let url = sessionDirectory(id: id).appendingPathComponent("session.json")
        guard fm.fileExists(atPath: url.path) else { throw SessionStoreError.sessionNotFound(id) }
        return try decoder.decode(ScanSession.self, from: Data(contentsOf: url))
    }

    func loadPage(sessionId: String, pageId: String) throws -> Page {
        let url = pageDir(sessionId: sessionId, pageId: pageId).appendingPathComponent("page.json")
        guard fm.fileExists(atPath: url.path) else { throw SessionStoreError.pageNotFound(pageId) }
        return try decoder.decode(Page.self, from: Data(contentsOf: url))
    }

    /// Appends capture: `original.jpg` + mid `working.png` (ARCHITECTURE §1.11).
    @discardableResult
    func appendPage(
        sessionId: String,
        originalJPEG: Data,
        workingPNG: Data? = nil,
        quad: Quad?,
        pageId: String = UUID().uuidString
    ) throws -> Page {
        lock.lock()
        defer { lock.unlock() }

        var session = try loadSession(id: sessionId)
        let index = session.pageIds.count
        let dir = pageDir(sessionId: sessionId, pageId: pageId)
        try ensureDir(dir)

        let png = try workingPNG ?? Self.pngFromJPEG(originalJPEG)
        guard Self.isPNG(png) else { throw SessionStoreError.notPNG }

        let originalURL = dir.appendingPathComponent("original.jpg")
        let workingURL = dir.appendingPathComponent("working.png")
        try originalJPEG.write(to: originalURL, options: .atomic)
        try png.write(to: workingURL, options: .atomic)

        let page = Page(
            id: pageId,
            sessionId: sessionId,
            index: index,
            originalUri: "pages/\(pageId)/original.jpg",
            croppedUri: nil,
            dewarpedUri: nil,
            cleanedUri: nil,
            status: .crop,
            quad: quad,
            metrics: nil,
            errorCode: nil,
            failedStage: nil
        )
        try writePage(page)
        session.pageIds.append(pageId)
        try writeSession(session)
        return page
    }

    func updatePageStatus(sessionId: String, pageId: String, status: PipelineStage) throws {
        lock.lock()
        defer { lock.unlock() }
        var page = try loadPage(sessionId: sessionId, pageId: pageId)
        page.status = status
        try writePage(page)
    }

    /// Persist a full `Page` snapshot (URIs, metrics, status, errorCode).
    func savePage(_ page: Page) throws {
        lock.lock()
        defer { lock.unlock() }
        try writePage(page)
    }

    /// Writes a lossless PNG under `pages/{pageId}/` and returns the relative URI.
    @discardableResult
    func writePagePNG(sessionId: String, pageId: String, fileName: String, data: Data) throws -> String {
        lock.lock()
        defer { lock.unlock() }
        guard Self.isPNG(data) else { throw SessionStoreError.notPNG }
        let dir = pageDir(sessionId: sessionId, pageId: pageId)
        try ensureDir(dir)
        let url = dir.appendingPathComponent(fileName)
        try data.write(to: url, options: .atomic)
        return "pages/\(pageId)/\(fileName)"
    }

    func readPageFile(sessionId: String, pageId: String, fileName: String) throws -> Data {
        let url = pageDir(sessionId: sessionId, pageId: pageId).appendingPathComponent(fileName)
        guard fm.fileExists(atPath: url.path) else { throw SessionStoreError.pageNotFound(pageId) }
        return try Data(contentsOf: url)
    }

    /// M2 stub: copy `working.png` → `cleaned.png` and set `cleanedUri` (ARCHITECTURE §6 PDF source).
    func finalizeStubCleaned(sessionId: String, pageId: String) throws {
        lock.lock()
        defer { lock.unlock() }
        var page = try loadPage(sessionId: sessionId, pageId: pageId)
        let dir = pageDir(sessionId: sessionId, pageId: pageId)
        let working = dir.appendingPathComponent("working.png")
        let cleaned = dir.appendingPathComponent("cleaned.png")
        guard fm.fileExists(atPath: working.path) else { throw SessionStoreError.pageNotFound(pageId) }
        if fm.fileExists(atPath: cleaned.path) {
            try fm.removeItem(at: cleaned)
        }
        try fm.copyItem(at: working, to: cleaned)
        page.cleanedUri = "pages/\(pageId)/cleaned.png"
        page.status = .done
        try writePage(page)
    }

    func updateExportPdfUri(sessionId: String, relativeUri: String?) throws {
        lock.lock()
        defer { lock.unlock() }
        var session = try loadSession(id: sessionId)
        session.exportPdfUri = relativeUri
        try writeSession(session)
    }

    func absoluteFile(sessionId: String, relativeUri: String) -> URL {
        sessionDirectory(id: sessionId).appendingPathComponent(relativeUri)
    }

    func pageDirPublic(sessionId: String, pageId: String) -> URL {
        pageDir(sessionId: sessionId, pageId: pageId)
    }

    private func pageDir(sessionId: String, pageId: String) -> URL {
        sessionDirectory(id: sessionId)
            .appendingPathComponent("pages", isDirectory: true)
            .appendingPathComponent(pageId, isDirectory: true)
    }

    private func ensureDir(_ url: URL) throws {
        try fm.createDirectory(at: url, withIntermediateDirectories: true)
    }

    private func writeSession(_ session: ScanSession) throws {
        let data = try encoder.encode(session)
        try data.write(
            to: sessionDirectory(id: session.id).appendingPathComponent("session.json"),
            options: .atomic
        )
    }

    private func writePage(_ page: Page) throws {
        let data = try encoder.encode(page)
        try data.write(
            to: pageDir(sessionId: page.sessionId, pageId: page.id).appendingPathComponent("page.json"),
            options: .atomic
        )
    }
}
