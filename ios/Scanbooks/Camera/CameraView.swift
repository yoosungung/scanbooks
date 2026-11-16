import SwiftUI

/// M2 Camera shell — 1페이지 mode. Live quad / AVFoundation = later slice.
struct CameraView: View {
    let pageCount: Int
    let onShutter: () -> Void
    let onOpenSession: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Preview placeholder (no camera permission loop in shell)
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [Color(white: 0.12), Color(white: 0.22)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .overlay {
                    QuadOverlay()
                }
                .ignoresSafeArea()

            VStack {
                topBar
                Spacer()
                Text("문서를 화면에 맞춰 주세요")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.bottom, 8)
                bottomBar
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .accessibilityElement(children: .contain)
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

private struct QuadOverlay: View {
    var body: some View {
        GeometryReader { geo in
            let inset = min(geo.size.width, geo.size.height) * 0.12
            Path { path in
                path.move(to: CGPoint(x: inset, y: inset * 1.2))
                path.addLine(to: CGPoint(x: geo.size.width - inset, y: inset))
                path.addLine(to: CGPoint(x: geo.size.width - inset * 0.9, y: geo.size.height - inset))
                path.addLine(to: CGPoint(x: inset * 1.1, y: geo.size.height - inset * 0.85))
                path.closeSubpath()
            }
            .stroke(Color.green.opacity(0.85), lineWidth: 2.5)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview {
    CameraView(pageCount: 1, onShutter: {}, onOpenSession: {})
}
