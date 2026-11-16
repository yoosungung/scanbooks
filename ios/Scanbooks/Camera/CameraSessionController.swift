import AVFoundation
import Combine
import Foundation
import Vision

final class CameraSessionController: NSObject, ObservableObject {
    @Published private(set) var edgeState: PreviewEdgeState = .undetected
    @Published private(set) var permission: CameraPermission = .unknown
    @Published private(set) var isRunning = false

    let session = AVCaptureSession()

    private let sessionQueue = DispatchQueue(label: "net.askwho.scanbooks.camera.session")
    private let visionQueue = DispatchQueue(label: "net.askwho.scanbooks.camera.vision")
    private let stateLock = NSLock()
    private var tracker = EdgeStabilityTracker(requiredHits: 3)
    private var didConfigure = false

    enum CameraPermission: Equatable {
        case unknown
        case authorized
        case denied
        case unavailable
    }

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            DispatchQueue.main.async { self.permission = .authorized }
            configureAndStart()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.permission = granted ? .authorized : .denied
                    if granted { self.configureAndStart() }
                }
            }
        case .denied, .restricted:
            DispatchQueue.main.async { self.permission = .denied }
        @unknown default:
            DispatchQueue.main.async { self.permission = .denied }
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
            DispatchQueue.main.async { self.isRunning = false }
        }
    }

    private func applyDetection(_ candidate: Quad?) {
        stateLock.lock()
        let next = tracker.ingest(candidate)
        stateLock.unlock()
        DispatchQueue.main.async { self.edgeState = next }
    }

    private func configureAndStart() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            if !self.didConfigure {
                self.session.beginConfiguration()
                self.session.sessionPreset = .high

                guard
                    let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
                        ?? AVCaptureDevice.default(for: .video),
                    let input = try? AVCaptureDeviceInput(device: device),
                    self.session.canAddInput(input)
                else {
                    self.session.commitConfiguration()
                    DispatchQueue.main.async { self.permission = .unavailable }
                    return
                }
                self.session.addInput(input)

                let output = AVCaptureVideoDataOutput()
                output.alwaysDiscardsLateVideoFrames = true
                output.videoSettings = [
                    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                ]
                output.setSampleBufferDelegate(self, queue: self.visionQueue)
                if self.session.canAddOutput(output) {
                    self.session.addOutput(output)
                    if let connection = output.connection(with: .video), connection.isVideoOrientationSupported {
                        connection.videoOrientation = .portrait
                    }
                }
                self.session.commitConfiguration()
                self.didConfigure = true
            }

            if !self.session.isRunning {
                self.session.startRunning()
            }
            DispatchQueue.main.async { self.isRunning = true }
        }
    }

    static func detectQuad(pixelBuffer: CVPixelBuffer, rough: RoughQuadDetector) -> Quad? {
        let request = VNDetectRectanglesRequest()
        request.minimumConfidence = 0.6
        request.maximumObservations = 1
        request.minimumAspectRatio = 0.3
        request.maximumAspectRatio = 1.0
        request.quadratureTolerance = 25
        request.minimumSize = 0.15

        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .up, options: [:])
        do {
            try handler.perform([request])
            if let obs = request.results?.first {
                let tl = NormPoint(x: Double(obs.topLeft.x), y: Double(1 - obs.topLeft.y))
                let tr = NormPoint(x: Double(obs.topRight.x), y: Double(1 - obs.topRight.y))
                let br = NormPoint(x: Double(obs.bottomRight.x), y: Double(1 - obs.bottomRight.y))
                let bl = NormPoint(x: Double(obs.bottomLeft.x), y: Double(1 - obs.bottomLeft.y))
                return Quad(tl: tl, tr: tr, br: br, bl: bl).clamped()
            }
        } catch {
            // fall through
        }
        return roughFromBGRA(pixelBuffer, detector: rough)
    }

    private static func roughFromBGRA(_ buffer: CVPixelBuffer, detector: RoughQuadDetector) -> Quad? {
        CVPixelBufferLockBaseAddress(buffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(buffer) else { return nil }
        let width = CVPixelBufferGetWidth(buffer)
        let height = CVPixelBufferGetHeight(buffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(buffer)
        let ptr = base.assumingMemoryBound(to: UInt8.self)

        let step = max(1, min(width, height) / 64)
        let dw = max(1, width / step)
        let dh = max(1, height / step)
        var gray = [UInt8](repeating: 0, count: dw * dh)
        for y in 0..<dh {
            for x in 0..<dw {
                let sx = min(width - 1, x * step)
                let sy = min(height - 1, y * step)
                let offset = sy * bytesPerRow + sx * 4
                let b = Double(ptr[offset])
                let g = Double(ptr[offset + 1])
                let r = Double(ptr[offset + 2])
                gray[y * dw + x] = UInt8(min(255, 0.299 * r + 0.587 * g + 0.114 * b))
            }
        }
        return detector.detect(pixels: gray, width: dw, height: dh)
    }
}

extension CameraSessionController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let candidate = CameraSessionController.detectQuad(pixelBuffer: pixelBuffer, rough: RoughQuadDetector())
        applyDetection(candidate)
    }
}
