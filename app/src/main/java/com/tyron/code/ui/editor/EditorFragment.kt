package com.tyron.code.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.tyron.code.R
import com.tyron.code.databinding.NewEditorFragmentBinding
import com.tyron.code.event.EventReceiver
import com.tyron.code.event.Unsubscribe
import com.tyron.code.highlighter.JavaFileHighlighter
import com.tyron.code.ui.legacyEditor.EditorView
import com.tyron.code.ui.main.MainViewModelV2
import com.tyron.code.ui.main.TextEditorState
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.subscribeEvent
import kotlinx.coroutines.flow.collect

class EditorFragment : Fragment() {

    private val viewModelV2: MainViewModelV2 by requireParentFragment().viewModels()
    private val viewModel: EditorViewModel by viewModels()

    private lateinit var binding: NewEditorFragmentBinding

    private val language = object: EmptyLanguage() {
        val analyzeManager = EditorView.TestAnalyzeManager(JavaFileHighlighter())

        override fun getAnalyzeManager(): AnalyzeManager {
            return analyzeManager
        }

        override fun requireAutoComplete(
            content: ContentReference,
            position: CharPosition,
            publisher: CompletionPublisher,
            extraArguments: Bundle
        ) {

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = NewEditorFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launchWhenResumed {
            viewModel.editorState.collect { editorState ->

                resetVisibilityStates()

                setLoadingContentViewVisibility(editorState.loadingContent)
                if (editorState.loadingContent) {
                    return@collect
                }

                editorState.loadingErrorMessage?.let {
                    setErrorViewVisibility(editorState.loadingErrorMessage)
                    return@collect
                }

                binding.editorView.setText(editorState.editorContent)
                binding.editorView.setEditorLanguage(language)
                binding.editorView.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    viewModel.commit(
                        event.action,
                        event.changeStart.index,
                        event.changeEnd.index,
                        event.changedText
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        binding.editorView.release()
    }

    private fun setErrorViewVisibility(loadingErrorMessage: String) {
        resetVisibilityStates()

        binding.editorContent.visibility = View.GONE
        binding.completionIndicatorContainer.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.errorTextView.text = loadingErrorMessage
    }

    private fun resetVisibilityStates() {
        binding.loadingView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.editorContent.visibility = View.VISIBLE
        binding.completionIndicatorContainer.visibility = View.VISIBLE
    }

    private fun setLoadingContentViewVisibility(visible: Boolean) {
        if (visible) {
            binding.loadingView.visibility = View.VISIBLE
            binding.editorContent.visibility = View.GONE
            binding.completionIndicatorContainer.visibility = View.GONE
        } else {
            binding.loadingView.visibility = View.GONE
            binding.editorContent.visibility = View.VISIBLE
            binding.completionIndicatorContainer.visibility = View.VISIBLE
        }
    }

}