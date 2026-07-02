package com.shadowlink.vpn.fragments

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.shadowlink.vpn.databinding.FragmentLogsBinding
import com.shadowlink.vpn.utils.AppLogger

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observer les logs centralisés
        AppLogger.logs.observe(viewLifecycleOwner) { entries ->
            val sb = StringBuilder()
            entries.forEach { e ->
                sb.append("[${e.timestamp}][${e.level}] ${e.message}\n")
            }
            binding.tvLogs.text = sb.toString()
            binding.scrollLogs.post {
                binding.scrollLogs.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
            }
        }

        binding.btnClearLogs.setOnClickListener {
            AppLogger.clearLogs()
        }

        binding.btnCopyLogs.setOnClickListener {
            val text = AppLogger.getLogsAsText()
            val cb   = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("ShadowLink Logs", text))
            Toast.makeText(context, "Logs copiés", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
