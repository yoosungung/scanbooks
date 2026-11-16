import SwiftUI

struct RootView: View {
    @State private var route: AppRoute = .camera
    @State private var sessionTitle: String = Self.defaultTitle()
    @State private var activeSessionId: String?
    @State private var activePageId: String?
    @State private var pageCount: Int = 0
    @State private var cleanedPageCount: Int = 0
    @State private var storeError: String?

    private let store: SessionStore = {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("scanbooks", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return SessionStore(rootDirectory: root)
    }()

    var body: some View {
        Group {
            switch route {
            case .camera:
                CameraView(
                    pageCount: pageCount,
                    onCaptured: { jpeg, quad in
                        persistCapture(jpeg: jpeg, quad: quad)
                    },
                    onOpenSession: {
                        refreshCounts()
                        route = .session
                    }
                )
            case .process:
                if let sessionId = activeSessionId, let pageId = activePageId {
                    ProcessStubView(
                        store: store,
                        sessionId: sessionId,
                        pageId: pageId,
                        onFinished: {
                            refreshCounts()
                            route = .camera
                        },
                        onCancel: { route = .camera }
                    )
                } else {
                    Color.black.ignoresSafeArea()
                        .onAppear { route = .camera }
                }
            case .session:
                SessionView(
                    title: $sessionTitle,
                    pageCount: pageCount,
                    cleanedPageCount: cleanedPageCount,
                    onBack: { route = .camera },
                    onExportPdf: {
                        guard let sessionId = activeSessionId else {
                            throw PdfExporterError.noCleanedPages
                        }
                        return try PdfExporter().export(sessionId: sessionId, store: store)
                    }
                )
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
            activePageId = page.id
            refreshCounts()
            route = .process
        } catch {
            storeError = "ScanSession/Page를 저장하지 못했습니다."
        }
    }

    private func refreshCounts() {
        guard let sessionId = activeSessionId,
              let session = try? store.loadSession(id: sessionId) else {
            pageCount = 0
            cleanedPageCount = 0
            return
        }
        pageCount = session.pageIds.count
        cleanedPageCount = session.pageIds.reduce(0) { count, pageId in
            let cleaned = (try? store.loadPage(sessionId: sessionId, pageId: pageId))?.cleanedUri != nil
            return count + (cleaned ? 1 : 0)
        }
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
