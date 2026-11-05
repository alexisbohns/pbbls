package app.pbbls.android.features.profile.support

import androidx.lifecycle.ViewModel
import app.pbbls.android.core.data.CrisisResourcesService
import app.pbbls.android.core.model.CrisisResources
import app.pbbls.android.core.model.defaultCountry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

/**
 * The Crisis resources screen's state (#1461): the bundled list and the country shown. The
 * country starts at the device region when listed, else France (the home market), else the
 * worldwide link alone when the list is empty.
 */
@HiltViewModel
class CrisisResourcesViewModel
    @Inject
    constructor(
        service: CrisisResourcesService,
    ) : ViewModel() {
        val resources: CrisisResources = service.resources

        private val _selectedCode = MutableStateFlow(resources.defaultCountry(Locale.getDefault().country)?.code)
        val selectedCode: StateFlow<String?> = _selectedCode.asStateFlow()

        fun select(code: String) {
            _selectedCode.value = code
        }
    }
