import SwiftUI

struct RootView: View {
    @State private var route: AppRoute = .camera
    @State private var pageCount: Int = 0
    @State private var sessionTitle: String = "새 스캔"

    var body: some View {
        Group {
            switch route {
            case .camera:
                CameraView(
                    pageCount: pageCount,
                    onShutter: { route = .process },
                    onOpenSession: { route = .session }
                )
            case .process:
                ProcessStubView(
                    onFinished: {
                        pageCount += 1
                        route = .camera
                    },
                    onCancel: { route = .camera }
                )
            case .session:
                SessionView(
                    title: $sessionTitle,
                    pageCount: pageCount,
                    onBack: { route = .camera }
                )
            }
        }
        .preferredColorScheme(.dark)
    }
}

#Preview {
    RootView()
}
