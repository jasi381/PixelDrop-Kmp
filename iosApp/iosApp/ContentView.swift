import SwiftUI
import Shared

/// Hosts the shared Compose Multiplatform console; everything below this
/// wrapper is common Kotlin code.
struct ComposeView: UIViewControllerRepresentable {
    let size: CGSize

    final class Coordinator {
        var partiallyOpen = false
        var size = CGSize.zero
        weak var host: UIViewController?

        /// shortcut: assumes the crease is at the window centre, which only holds full-screen;
        /// Split View / Stage windows report flat instead.
        func reportHinge() {
            let fullScreen = host?.view.window.map { $0.bounds.size == $0.screen.bounds.size } ?? true
            MainViewControllerKt.setHinge(
                partiallyOpen: partiallyOpen && fullScreen,
                landscape: size.width > size.height
            )
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIViewController(context: Context) -> UIViewController {
        let vc = MainViewControllerKt.MainViewController()
        let coordinator = context.coordinator
        coordinator.host = vc
        coordinator.size = size
        if #available(iOS 27.1, *) {
            vc.view.addInteraction(UIHingeInteraction { _, update in
                coordinator.partiallyOpen = update.hinge?.status == .partiallyOpen
                coordinator.reportHinge()
            })
        }
        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        context.coordinator.size = size
        context.coordinator.reportHinge()
    }
}

struct ContentView: View {
    var body: some View {
        GeometryReader { proxy in
            ComposeView(size: proxy.size)
        }
        .ignoresSafeArea(.all)
        .background(Color.black)
    }
}
