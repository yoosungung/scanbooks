import SwiftUI

/// Settings — privacy notice + OSS attribution + About (docs/UIUX.md §4.7).
struct SettingsView: View {
    let onBack: () -> Void
    var modelVersion: String = AttributionPresentation.defaultModelVersion

    var body: some View {
        NavigationStack {
            List {
                Section(AttributionPresentation.sectionPrivacy) {
                    Text(AttributionPresentation.privacyBody)
                        .font(.body)
                        .foregroundStyle(.primary)
                        .accessibilityLabel(AttributionPresentation.privacyBody)
                }

                Section(AttributionPresentation.sectionAttribution) {
                    ForEach(AttributionPresentation.creditLines, id: \.self) { line in
                        Text(line)
                            .font(.subheadline)
                    }
                    Text(AttributionPresentation.nonCommercialNotice)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Link("DocTr 리포지토리", destination: AttributionPresentation.doctrRepoURL)
                    Link("DocTr-Plus LICENSE", destination: AttributionPresentation.doctrLicenseURL)
                }

                Section(AttributionPresentation.sectionAbout) {
                    Text(AttributionPresentation.aboutModelVersion(modelVersion))
                        .font(.subheadline)
                        .accessibilityLabel(AttributionPresentation.aboutModelVersion(modelVersion))
                }
            }
            .navigationTitle("설정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("닫기", action: onBack)
                }
            }
        }
    }
}

#Preview {
    SettingsView(onBack: {})
}
