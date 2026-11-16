import SwiftUI

/// M2 Camera — full-bleed preview + rough/Vision realtime quad (UIUX §4.1).
struct CameraView: View {
    let pageCount: Int
    let onShutter: () -> Void
    let onOpenSession: () -> Void

    @StateObject private var camera = CameraSessionController()
    @AppStorage("scanbooks.didShowDewarpTip") private var didShowDewarpTip = false
    @State private var showDewarpTip = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if camera.permission == .authorized {
                CameraPreviewView(session: camera.session)
                    .ignoresSafeArea()
            } else {
                placeholderPreview
            }

            QuadOverlayLayer(state: camera.edgeState)
                .ignoresSafeArea()
                .allowsHitTesting(false)

            VStack {
                topBar
                Spacer()
                if !camera.edgeState.hintCopy.isEmpty {
                    Text(camera.edgeState.hintCopy)
                        .font(.footnote)
                        .foregroundStyle(.white.opacity(0.85))
                        .padding(.bottom, 8)
                }
                if camera.permission == .denied {
                    permissionDeniedBanner
                        .padding(.bottom, 12)
                }
                bottomBar
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .onAppear {
            camera.start()
            if !didShowDewarpTip {
                showDewarpTip = true
                didShowDewarpTip = true
            }
        }
        .onDisappear { camera.stop() }
        .alert("촬영 후 곡면을 펴고 손가락을 지웁니다", isPresented: $showDewarpTip) {
            Button("확인", role: .cancel) {}
        }
        .accessibilityElement(children: .contain)
    }

    private var placeholderPreview: some View {
        Rectangle()
            .fill(
                LinearGradient(
                    colors: [Color(white: 0.12), Color(white: 0.22)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .ignoresSafeArea()
    }

    private var permissionDeniedBanner: some View {
        VStack(spacing: 8) {
            Text("카메라 권한이 필요합니다")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
            Button("설정 열기") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(12)
        .background(Color.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
    }

    private var topBar: some View {
        HStack {
            Text("1페이지")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.15), in: Capsule())
            Spacer()
            Image(systemName: "ellipsis")
                .foregroundStyle(.white)
                .accessibilityLabel("설정")
                .opacity(0.5)
        }
    }

    private var bottomBar: some View {
        HStack(alignment: .center) {
            Label("손가락 지우기", systemImage: "hand.raised.fill")
                .font(.caption)
                .foregroundStyle(.white)
                .padding(8)
                .background(Color.white.opacity(0.12), in: Capsule())

            Spacer()

            Button(action: onShutter) {
                Circle()
                    .strokeBorder(.white, lineWidth: 4)
                    .frame(width: 72, height: 72)
                    .overlay(
                        Circle()
                            .fill(.white)
                            .frame(width: 58, height: 58)
                    )
            }
            .accessibilityLabel("셔터")

            Spacer()

            Button(action: onOpenSession) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.white.opacity(0.2))
                        .frame(width: 52, height: 64)
                    if pageCount > 0 {
                        Text("\(pageCount)")
                            .font(.headline)
                            .foregroundStyle(.white)
                    } else {
                        Image(systemName: "photo")
                            .foregroundStyle(.white.opacity(0.6))
                    }
                }
            }
            .accessibilityLabel("세션 보기")
        }
        .padding(.bottom, 8)
    }
}

private struct QuadOverlayLayer: View {
    let state: PreviewEdgeState

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            switch state.overlayKind {
            case .grayGuide:
                Path { path in
                    let inset = min(size.width, size.height) * 0.12
                    path.addRect(
                        CGRect(
                            x: inset,
                            y: inset,
                            width: size.width - inset * 2,
                            height: size.height - inset * 2
                        )
                    )
                }
                .stroke(Color.gray.opacity(0.7), lineWidth: 1.5)
            case .greenQuad:
                if let quad = state.quad {
                    Path(quad.path(in: size))
                        .stroke(Color.green.opacity(0.9), lineWidth: 2.5)
                }
            }
        }
        .accessibilityHidden(true)
    }
}

#Preview {
    CameraView(pageCount: 1, onShutter: {}, onOpenSession: {})
}
