import SwiftUI
import Shared

/// Hosts the shared Compose Multiplatform console; everything below this
/// wrapper is common Kotlin code.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .background(Color.black)
    }
}
