package app.xnkfit.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.domain.share.CardFacts
import app.xnkfit.domain.share.CardKind
import app.xnkfit.domain.share.CardSize
import app.xnkfit.domain.share.CardSpec
import app.xnkfit.domain.share.CardStyle
import app.xnkfit.domain.share.CardTemplate
import app.xnkfit.domain.share.ShareOptions
import app.xnkfit.domain.share.availableKinds
import app.xnkfit.domain.share.buildCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareState(
    val loaded: Boolean = false,
    val facts: CardFacts = CardFacts(),
    val sayaHidesWeight: Boolean = false,
    val photoPath: String? = null,
    val options: ShareOptions = ShareOptions(),
) {
    val available: List<CardKind> get() = availableKinds(facts)

    /** The card for the current options, or null when the phone has no data for it. */
    val spec: CardSpec? get() = buildCard(facts, options, sayaHidesWeight, photoPath)

    /** One spec per kind for the gallery, with the current style and size. */
    fun galleryCards(): List<CardSpec> = available.mapNotNull { buildCard(facts, options.copy(kind = it), sayaHidesWeight, photoPath) }
}

/** Kartu saya and the editor (PRD P-6). All card rules live in `domain.share`; loaders are lambdas so the model needs no Room type. */
class ShareViewModel(
    private val loadFacts: suspend () -> CardFacts,
    private val loadPhotoPath: suspend () -> String?,
    private val loadSayaHidesWeight: suspend () -> Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                val facts = loadFacts()
                val photo = loadPhotoPath()
                val hides = loadSayaHidesWeight()
                _state.update { it.copy(loaded = true, facts = facts, photoPath = photo, sayaHidesWeight = hides) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A read failure must not crash the screen; it shows the empty state.
                _state.update { it.copy(loaded = true) }
            }
        }
    }

    private fun edit(f: (ShareOptions) -> ShareOptions) = _state.update { it.copy(options = f(it.options)) }

    fun selectKind(kind: CardKind) = edit { it.copy(kind = kind) }
    fun setStyle(style: CardStyle) = edit { it.copy(style = style) }
    fun setSize(size: CardSize) = edit { it.copy(size = size) }
    fun setTemplate(template: CardTemplate) = edit { it.copy(template = template) }
    fun setBrand(on: Boolean) = edit { it.copy(brand = on) }
    fun setHideKg(on: Boolean) = edit { it.copy(hideKg = on) }
    fun setBlurFaces(on: Boolean) = edit { it.copy(blurFaces = on) }
    fun setWithPhoto(on: Boolean) = edit { it.copy(withPhoto = on) }
}
