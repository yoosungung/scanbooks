import SwiftUI

/// Simple Session shell + PDF CTA path (real PdfDocument export = later M2 slice).
struct SessionView: View {
    @Binding var title: String
    let pageCount: Int
    let onBack: () -> Void

    @State private var showPdfStub = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                TextField("세션 제목", text: $title)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3.weight(.semibold))

                Text("\(pageCount)장")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 12) {
                    if pageCount == 0 {
                        Text("촬영한 페이지가 없습니다")
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        ForEach(0..<pageCount, id: \.self) { index in
                            RoundedRectangle(cornerRadius: 8)
                                .fill(Color.secondary.opacity(0.25))
                                .aspectRatio(3 / 4, contentMode: .fit)
                                .overlay {
                                    Text("\(index + 1)")
                                        .font(.headline)
                                }
                                .overlay(alignment: .topTrailing) {
                                    Text("완료")
                                        .font(.caption2.weight(.semibold))
                                        .padding(4)
                                        .background(.green.opacity(0.85), in: Capsule())
                                        .padding(6)
                                }
                        }
                    }
                }

                Spacer()

                Button {
                    showPdfStub = true
                } label: {
                    Label("PDF로 내보내기", systemImage: "doc.richtext")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(pageCount == 0)
                .accessibilityLabel("PDF로 내보내기")
            }
            .padding()
            .navigationTitle("세션")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("카메라", action: onBack)
                }
            }
            .alert("PDF 경로 (stub)", isPresented: $showPdfStub) {
                Button("확인", role: .cancel) {}
            } message: {
                Text("완료된 \(pageCount)장을 PDF로 내보내는 경로는 연결됐습니다. 실제 PDFKit 쓰기는 후속 M2 슬라이스에서 구현합니다.")
            }
        }
    }
}

#Preview {
    SessionView(title: .constant("테스트"), pageCount: 2, onBack: {})
}
