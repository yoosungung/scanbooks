import SwiftUI

struct RootView: View {
    @State private var route: AppRoute = .camera
    @State private var sessionTitle: String = Self.defaultTitle()
    @State private var activeSessionId: String?
    @State private var pageCount: Int = 0
    @State private var cleanedPageCount: Int = 0
    @State private var processingCount: Int = 0
    @State private var sessionPages: [SessionPageRow] = []
    @State private var storeError: String?

    private let store: SessionStore = {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("scanbooks", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return SessionStore(rootDirectory: root)
    }()
    private let runner = OrtBackendFactory.makeRunner()

    var body: some View {
        Group {
            switch route {
            case .camera:
                CameraView(
                    pageCount: pageCount,
                    processingCount: processingCount,
                    onCaptured: { jpeg, quad in
                        persistCapture(jpeg: jpeg, quad: quad)
                    },
                    onOpenSession: {
                        refreshSessionState()
                        route = .session
                    },
                    onOpenSettings: { route = .settings }
                )
            case .process:
                // Retained for failed-page retry path; continuous batch stays on camera.
                Color.black.ignoresSafeArea()
                    .onAppear { route = .camera }
            case .session:
                SessionView(
                    title: $sessionTitle,
                    pages: sessionPages,
                    cleanedPageCount: cleanedPageCount,
                    onBack: { route = .camera },
                    onAddScan: { route = .camera },
                    onMove: { pageId, toIndex in
                        movePage(pageId: pageId, toIndex: toIndex)
                    },
                    onDelete: { pageId in
                        deletePage(pageId: pageId)
                    },
                    onExportPdf: {
                        guard let sessionId = activeSessionId else {
                            throw PdfExporterError.noCleanedPages
                        }
                        return try PdfExporter().export(sessionId: sessionId, store: store)
                    }
                )
            case .settings:
                SettingsView(onBack: { route = .camera })
            }
        }
        .preferredColorScheme(.dark)
        .alert("저장 실패", isPresented: Binding(
            get: { storeError != nil },
            set: { if !$0 { storeError = nil } }
        )) {
            Button("확인", role: .cancel) { storeError = nil }
        } message: {
            Text(storeError ?? "")
        }
    }

    /// Continuous batch: append + background pipeline, stay on Camera (UIUX §3.2).
    private func persistCapture(jpeg: Data, quad: Quad?) {
        do {
            let sessionId: String
            if let existing = activeSessionId {
                sessionId = existing
            } else {
                let session = try store.createSession(title: sessionTitle)
                activeSessionId = session.id
                sessionId = session.id
            }
            let page = try store.appendPage(sessionId: sessionId, originalJPEG: jpeg, quad: quad)
            refreshSessionState()
            runner.run(store: store, sessionId: sessionId, pageId: page.id) { _ in
                refreshSessionState()
            }
        } catch {
            storeError = "ScanSession/Page를 저장하지 못했습니다."
        }
    }

    private func movePage(pageId: String, toIndex: Int) {
        guard let sessionId = activeSessionId,
              let session = try? store.loadSession(id: sessionId),
              let from = session.pageIds.firstIndex(of: pageId),
              let next = SessionPageOrder.move(pageIds: session.pageIds, from: from, to: toIndex)
        else { return }
        do {
            try store.reorderPages(sessionId: sessionId, pageIds: next)
            refreshSessionState()
        } catch {
            storeError = "페이지 순서를 바꾸지 못했습니다."
        }
    }

    private func deletePage(pageId: String) {
        guard let sessionId = activeSessionId else { return }
        do {
            try store.deletePage(sessionId: sessionId, pageId: pageId)
            refreshSessionState()
        } catch {
            storeError = "페이지를 삭제하지 못했습니다."
        }
    }

    private func refreshSessionState() {
        guard let sessionId = activeSessionId,
              let session = try? store.loadSession(id: sessionId) else {
            pageCount = 0
            cleanedPageCount = 0
            processingCount = 0
            sessionPages = []
            return
        }
        pageCount = session.pageIds.count
        var cleaned = 0
        var statuses: [PipelineStage] = []
        var rows: [SessionPageRow] = []
        for pageId in session.pageIds {
            guard let page = try? store.loadPage(sessionId: sessionId, pageId: pageId) else { continue }
            statuses.append(page.status)
            rows.append(SessionPageRow(id: page.id, status: page.status))
            if page.cleanedUri != nil { cleaned += 1 }
        }
        cleanedPageCount = cleaned
        processingCount = BatchScanPresentation.processingCount(statuses: statuses)
        sessionPages = rows
    }

    private static func defaultTitle() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "yyyy.MM.dd HH:mm"
        return f.string(from: Date())
    }
}

#Preview {
    RootView()
}
