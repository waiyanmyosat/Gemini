import { motion } from "motion/react";
import { Smartphone, Github, Download, ShieldCheck, Code2, Sparkles } from "lucide-react";

export default function App() {
  return (
    <div className="min-h-screen bg-[#0a0a0a] text-white font-sans selection:bg-blue-500/30">
      {/* Background Glow */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-blue-600/10 blur-[120px] rounded-full" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-purple-600/10 blur-[120px] rounded-full" />
      </div>

      <div className="relative max-w-6xl mx-auto px-6 py-12">
        {/* Header */}
        <header className="flex flex-col md:flex-row items-center justify-between mb-20 gap-8">
          <motion.div 
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            className="flex items-center gap-4"
          >
            <div className="w-12 h-12 bg-gradient-to-tr from-blue-600 to-purple-600 rounded-xl flex items-center justify-center shadow-lg shadow-blue-500/20">
              <Sparkles className="text-white w-7 h-7" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">Gemini Native</h1>
              <p className="text-gray-400 text-sm">Android Wrapper • com.gemini.ai</p>
            </div>
          </motion.div>

          <motion.div 
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="flex gap-4"
          >
            <a 
              href="https://github.com" 
              target="_blank" 
              rel="noreferrer"
              className="flex items-center gap-2 px-4 py-2 bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg transition-all"
            >
              <Github className="w-4 h-4" />
              <span>GitHub Actions</span>
            </a>
            <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg transition-all shadow-lg shadow-blue-600/20">
              <Download className="w-4 h-4" />
              <span>Download APK</span>
            </button>
          </motion.div>
        </header>

        <main className="grid grid-cols-1 lg:grid-cols-12 gap-12">
          {/* Left Column: Info */}
          <div className="lg:col-span-7 space-y-12">
            <motion.section 
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 }}
            >
              <h2 className="text-4xl font-bold mb-6 bg-gradient-to-r from-white to-gray-400 bg-clip-text text-transparent">
                Native Android Wrapper for Google Gemini
              </h2>
              <p className="text-lg text-gray-400 leading-relaxed max-w-2xl">
                A high-performance Kotlin-based Android application targeting Android 16 (API 36). 
                Optimized for arm64 architecture with seamless WebView integration and official branding.
              </p>
            </motion.section>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {[
                { icon: Smartphone, title: "Android 16 Ready", desc: "Targeting API 36 with modern Kotlin practices." },
                { icon: ShieldCheck, title: "Secure WebView", desc: "Hardened configuration with DOM storage and JS enabled." },
                { icon: Code2, title: "Clean Architecture", desc: "Standard Gradle structure ready for production." },
                { icon: Github, title: "CI/CD Pipeline", desc: "Automated builds via GitHub Actions workflow." }
              ].map((feature, i) => (
                <motion.div 
                  key={i}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.2 + i * 0.1 }}
                  className="p-6 bg-white/5 border border-white/10 rounded-2xl hover:border-blue-500/30 transition-colors group"
                >
                  <feature.icon className="w-8 h-8 text-blue-500 mb-4 group-hover:scale-110 transition-transform" />
                  <h3 className="text-lg font-semibold mb-2">{feature.title}</h3>
                  <p className="text-gray-400 text-sm">{feature.desc}</p>
                </motion.div>
              ))}
            </div>

            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.6 }}
              className="p-8 bg-gradient-to-br from-blue-600/10 to-purple-600/10 border border-blue-500/20 rounded-3xl"
            >
              <h3 className="text-xl font-bold mb-4 flex items-center gap-2">
                <Github className="w-5 h-5 text-blue-400" />
                Build Instructions
              </h3>
              <ol className="space-y-4 text-gray-300">
                <li className="flex gap-3">
                  <span className="flex-shrink-0 w-6 h-6 bg-blue-500/20 text-blue-400 rounded-full flex items-center justify-center text-xs font-bold">1</span>
                  <span>Push this code to a GitHub repository.</span>
                </li>
                <li className="flex gap-3">
                  <span className="flex-shrink-0 w-6 h-6 bg-blue-500/20 text-blue-400 rounded-full flex items-center justify-center text-xs font-bold">2</span>
                  <span>GitHub Actions will automatically trigger a build in the <b>Actions</b> tab.</span>
                </li>
                <li className="flex gap-3">
                  <span className="flex-shrink-0 w-6 h-6 bg-blue-500/20 text-blue-400 rounded-full flex items-center justify-center text-xs font-bold">3</span>
                  <span>Download the generated APK from the build artifacts.</span>
                </li>
              </ol>
            </motion.div>
          </div>

          {/* Right Column: Phone Mockup */}
          <div className="lg:col-span-5 flex justify-center items-start">
            <motion.div 
              initial={{ opacity: 0, scale: 0.9, rotate: 2 }}
              animate={{ opacity: 1, scale: 1, rotate: 0 }}
              transition={{ delay: 0.3, type: "spring", stiffness: 100 }}
              className="relative w-[300px] h-[600px] bg-[#1a1a1a] rounded-[3rem] border-[8px] border-[#333] shadow-2xl overflow-hidden shadow-blue-500/10"
            >
              {/* Notch */}
              <div className="absolute top-0 left-1/2 -translate-x-1/2 w-32 h-7 bg-[#333] rounded-b-2xl z-20" />
              
              {/* Screen Content */}
              <div className="absolute inset-0 bg-white flex flex-col">
                <div className="h-14 bg-white border-b flex items-center px-6 justify-between pt-4">
                  <div className="w-4 h-4 rounded-full bg-gray-200" />
                  <div className="flex gap-1">
                    <div className="w-3 h-3 rounded-full bg-gray-200" />
                    <div className="w-3 h-3 rounded-full bg-gray-200" />
                  </div>
                </div>
                <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
                  <motion.div 
                    animate={{ 
                      scale: [1, 1.1, 1],
                      rotate: [0, 5, -5, 0]
                    }}
                    transition={{ duration: 4, repeat: Infinity }}
                    className="w-24 h-24 bg-gradient-to-tr from-blue-600 to-purple-600 rounded-3xl flex items-center justify-center mb-8 shadow-xl shadow-blue-500/20"
                  >
                    <Sparkles className="text-white w-12 h-12" />
                  </motion.div>
                  <h4 className="text-black text-2xl font-bold mb-2">Gemini</h4>
                  <p className="text-gray-500 text-sm mb-8">Google's AI Assistant</p>
                  <div className="w-full space-y-3">
                    <div className="h-10 bg-gray-100 rounded-lg w-full" />
                    <div className="h-10 bg-gray-100 rounded-lg w-full" />
                    <div className="h-10 bg-blue-600 rounded-lg w-full" />
                  </div>
                </div>
                <div className="h-16 bg-gray-50 border-t flex items-center justify-around px-4">
                  <div className="w-8 h-8 rounded-full bg-gray-200" />
                  <div className="w-8 h-8 rounded-full bg-gray-200" />
                  <div className="w-8 h-8 rounded-full bg-gray-200" />
                </div>
              </div>
            </motion.div>
          </div>
        </main>

        <footer className="mt-24 pt-8 border-t border-white/5 text-center text-gray-500 text-sm">
          <p>© 2024 Gemini Native Android Wrapper. Built for high-performance AI interaction.</p>
        </footer>
      </div>
    </div>
  );
}
