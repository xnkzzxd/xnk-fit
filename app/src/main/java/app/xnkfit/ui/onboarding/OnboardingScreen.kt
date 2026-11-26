package app.xnkfit.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.xnkfit.R
import app.xnkfit.ui.adaptive.FlowWidth
import app.xnkfit.domain.rules.InputProblem
import app.xnkfit.domain.rules.Sex
import app.xnkfit.ui.common.ChoiceCard
import app.xnkfit.ui.common.MinTouch
import app.xnkfit.ui.common.StepButtons
import app.xnkfit.ui.screening.ScreeningResultScreen
import app.xnkfit.ui.screening.ScreeningScreen

private const val STEPS_SHOWN = 6

/** Number of the step in "Langkah n/6"; the form and its result count as the health step. */
fun stepNumber(step: OnboardingStep): Int? = when (step) {
    OnboardingStep.GOAL -> 1
    OnboardingStep.BODY -> 2
    OnboardingStep.INTEREST -> 3
    OnboardingStep.NAME -> 4
    OnboardingStep.HEALTH, OnboardingStep.SCREENING, OnboardingStep.RESULT -> 5
    OnboardingStep.PERMISSIONS -> 6
    OnboardingStep.WELCOME, OnboardingStep.UNDER_13 -> null
}

/** Onboarding host (PRD O-1…O-6). No account, no sign-in (O-6). [onDone] runs after the profile is saved. */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = s.step != OnboardingStep.WELCOME) { vm.back() }
    LaunchedEffect(s.finished) { if (s.finished) onDone() }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        FlowWidth {
        Column(Modifier.fillMaxSize()) {
            stepNumber(s.step)?.let {
                Text(
                    stringResource(R.string.ob_step_of, it, STEPS_SHOWN),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                )
            }
            when (s.step) {
                OnboardingStep.WELCOME -> WelcomeStep(onStart = vm::start)
                OnboardingStep.GOAL -> GoalStep(s, vm)
                OnboardingStep.BODY -> BodyStep(s, vm)
                OnboardingStep.INTEREST -> InterestStep(s, vm)
                OnboardingStep.NAME -> NameStep(s, vm)
                OnboardingStep.HEALTH -> HealthStep(vm)
                OnboardingStep.SCREENING -> ScreeningScreen(
                    screening = s.screening, incomplete = s.screeningIncomplete,
                    onAnswer = vm::answer, onSave = vm::saveScreening, onBack = { vm.back() },
                )
                OnboardingStep.RESULT -> s.outcome?.let {
                    ScreeningResultScreen(it, onContinue = vm::next, onBack = { vm.back() })
                }
                OnboardingStep.PERMISSIONS -> PermissionsStep(
                    s, vm, onEnter = { scope.launch { vm.finish() } },
                )
                OnboardingStep.UNDER_13 -> Under13Step(onChangeYear = { vm.back() })
            }
        }
        }
    }
}

@Composable
private fun StepColumn(title: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(stringResource(R.string.ob_brand), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.ob_tagline), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.ob_privacy_line), style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(stringResource(R.string.ob_point_gear))
            Text(stringResource(R.string.ob_point_food))
            Text(stringResource(R.string.ob_point_offline))
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
            Text(stringResource(R.string.ob_start))
        }
        // Restore arrives with backup (B-16); shown so the layout matches the mockup, but not usable yet.
        // Disabled, but the text is meaningful: keep it at a readable contrast (the default 38 % fade failed 4.5:1 in dark mode).
        OutlinedButton(
            onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Text(stringResource(R.string.ob_restore))
        }
    }
}

@Composable
private fun GoalStep(s: OnboardingState, vm: OnboardingViewModel) = StepColumn(R.string.ob_goal_title) {
    GoalChoice.entries.forEach {
        ChoiceCard(s.goal == it, stringResource(it.title), hint = stringResource(it.hint)) { vm.selectGoal(it) }
    }
    StepButtons(
        nextLabel = stringResource(R.string.ob_next), onNext = vm::next, nextEnabled = s.goal != null,
        backLabel = stringResource(R.string.ob_back), onBack = { vm.back() },
    )
}

@StringRes
private fun problemText(p: InputProblem): Int = when (p) {
    InputProblem.SEX_MISSING -> R.string.ob_err_sex
    InputProblem.BIRTH_YEAR_MISSING -> R.string.ob_err_birth_missing
    InputProblem.BIRTH_YEAR_IN_FUTURE, InputProblem.AGE_OUT_OF_RANGE -> R.string.ob_err_birth_range
    InputProblem.HEIGHT_MISSING -> R.string.ob_err_height_missing
    InputProblem.HEIGHT_OUT_OF_RANGE -> R.string.ob_err_height_range
    InputProblem.WEIGHT_MISSING -> R.string.ob_err_weight_missing
    InputProblem.WEIGHT_OUT_OF_RANGE -> R.string.ob_err_weight_range
    // Activity and goal are not typed on this step; the flow never lets them be missing.
    InputProblem.ACTIVITY_MISSING, InputProblem.GOAL_MISSING -> R.string.ob_err_generic
}

@Composable
private fun BodyStep(s: OnboardingState, vm: OnboardingViewModel) = StepColumn(R.string.ob_body_title) {
    Text(stringResource(R.string.ob_body_private), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        ChoiceCard(s.sex == Sex.PEREMPUAN, stringResource(R.string.ob_sex_female), Modifier.weight(1f)) { vm.selectSex(Sex.PEREMPUAN) }
        ChoiceCard(s.sex == Sex.LAKI_LAKI, stringResource(R.string.ob_sex_male), Modifier.weight(1f)) { vm.selectSex(Sex.LAKI_LAKI) }
    }
    ErrorLine(InputProblem.SEX_MISSING in s.problems, R.string.ob_err_sex)
    val birthProblem = s.problems.firstOrNull {
        it == InputProblem.BIRTH_YEAR_MISSING || it == InputProblem.BIRTH_YEAR_IN_FUTURE || it == InputProblem.AGE_OUT_OF_RANGE
    }
    NumberField(R.string.ob_birth_year, s.birthYearText, vm::setBirthYear, birthProblem?.let(::problemText), KeyboardType.Number)
    val heightProblem = s.problems.firstOrNull { it == InputProblem.HEIGHT_MISSING || it == InputProblem.HEIGHT_OUT_OF_RANGE }
    Stepper(R.string.ob_height, s.heightText, vm::setHeight, heightProblem?.let(::problemText),
        R.string.ob_height_less, R.string.ob_height_more, { vm.stepHeight(-1) }, { vm.stepHeight(1) })
    val weightProblem = s.problems.firstOrNull { it == InputProblem.WEIGHT_MISSING || it == InputProblem.WEIGHT_OUT_OF_RANGE }
    Stepper(R.string.ob_weight, s.weightText, vm::setWeight, weightProblem?.let(::problemText),
        R.string.ob_weight_less, R.string.ob_weight_more, { vm.stepWeight(-1) }, { vm.stepWeight(1) })
    StepButtons(
        nextLabel = stringResource(R.string.ob_next), onNext = vm::next,
        backLabel = stringResource(R.string.ob_back), onBack = { vm.back() },
    )
}

@Composable
private fun ErrorLine(show: Boolean, @StringRes text: Int) {
    if (show) Text(stringResource(text), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun NumberField(
    @StringRes label: Int, value: String, onChange: (String) -> Unit, @StringRes error: Int?,
    keyboard: KeyboardType, modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth().heightIn(min = MinTouch),
    )
}

@Composable
private fun Stepper(
    @StringRes label: Int, value: String, onChange: (String) -> Unit, @StringRes error: Int?,
    @StringRes lessDescription: Int, @StringRes moreDescription: Int, onLess: () -> Unit, onMore: () -> Unit,
) {
    val less = stringResource(lessDescription)
    val more = stringResource(moreDescription)
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.Top) {
        OutlinedButton(onClick = onLess, modifier = Modifier.heightIn(min = MinTouch).semantics { contentDescription = less }) {
            Text(stringResource(R.string.ob_minus))
        }
        NumberField(label, value, onChange, error, KeyboardType.Decimal, Modifier.weight(1f))
        OutlinedButton(onClick = onMore, modifier = Modifier.heightIn(min = MinTouch).semantics { contentDescription = more }) {
            Text(stringResource(R.string.ob_plus))
        }
    }
}

@Composable
private fun InterestStep(s: OnboardingState, vm: OnboardingViewModel) = StepColumn(R.string.ob_interest_title) {
    Interest.entries.forEach { ChoiceCard(s.interest == it, stringResource(it.title)) { vm.selectInterest(it) } }
    StepButtons(
        nextLabel = stringResource(R.string.ob_next), onNext = vm::next, nextEnabled = s.interest != null,
        backLabel = stringResource(R.string.ob_back), onBack = { vm.back() },
    )
}

@Composable
private fun NameStep(s: OnboardingState, vm: OnboardingViewModel) = StepColumn(R.string.ob_name_title) {
    Text(stringResource(R.string.ob_name_optional), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = s.nickname, onValueChange = vm::setNickname, singleLine = true,
        label = { Text(stringResource(R.string.ob_name_title)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
    )
    TextButton(onClick = vm::skipNickname, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.ob_skip)) }
    StepButtons(
        nextLabel = stringResource(R.string.ob_next), onNext = vm::next,
        backLabel = stringResource(R.string.ob_back), onBack = { vm.back() },
    )
}

@Composable
private fun HealthStep(vm: OnboardingViewModel) = StepColumn(R.string.ob_health_title) {
    Text(stringResource(R.string.ob_health_sub), style = MaterialTheme.typography.bodyMedium)
    ChoiceCard(false, stringResource(R.string.ob_health_yes), hint = stringResource(R.string.ob_health_yes_hint)) { vm.answerHealth(true) }
    ChoiceCard(false, stringResource(R.string.ob_health_no), hint = stringResource(R.string.ob_health_no_hint)) { vm.answerHealth(false) }
    OutlinedButton(onClick = { vm.back() }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.ob_back)) }
}

/**
 * Permissions (PRD O-3): each row can be skipped and is asked again where it is used. Only notifications and
 * camera are asked here; Health Connect and Google backup have their own libraries in later tasks (B-14, B-16),
 * so for now they only say they will ask at the moment of use. Nothing outside Design §14 is requested.
 */
@Composable
private fun PermissionsStep(s: OnboardingState, vm: OnboardingViewModel, onEnter: () -> Unit) =
    StepColumn(R.string.ob_perm_title) {
        Text(stringResource(R.string.ob_perm_sub), style = MaterialTheme.typography.bodyMedium)
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.permissionHandled(PermissionItem.NOTIFICATIONS)
        }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.permissionHandled(PermissionItem.CAMERA)
        }
        PermissionItem.entries.forEach { item ->
            val handled = item in s.permissionsHandled
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(item.why), style = MaterialTheme.typography.bodyMedium)
                        if (handled) Text(stringResource(R.string.ob_perm_done), style = MaterialTheme.typography.labelLarge)
                    }
                    if (item.asksHere && !handled) {
                        Button(
                            modifier = Modifier.heightIn(min = MinTouch),
                            onClick = {
                                when (item) {
                                    PermissionItem.NOTIFICATIONS ->
                                        // Before Android 13 notifications need no prompt.
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            vm.permissionHandled(item)
                                        }
                                    PermissionItem.CAMERA -> camera.launch(Manifest.permission.CAMERA)
                                    else -> Unit
                                }
                            },
                        ) { Text(stringResource(R.string.ob_perm_allow)) }
                    }
                    if (!handled) {
                        TextButton(onClick = { vm.permissionHandled(item) }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.ob_perm_later))
                        }
                    }
                }
            }
        }
        if (s.saveFailed) {
            Text(stringResource(R.string.ob_save_failed), color = MaterialTheme.colorScheme.error)
        }
        StepButtons(
            nextLabel = stringResource(R.string.ob_enter), onNext = onEnter, nextEnabled = !s.saving,
            backLabel = stringResource(R.string.ob_back), onBack = { vm.back() },
        )
    }

/** PRD O-5: explain, keep nothing. "Ubah tahun lahir" goes back to the body form, now empty. */
@Composable
private fun Under13Step(onChangeYear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(stringResource(R.string.ob_brand), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.ob_u13_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.ob_u13_thanks), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.ob_u13_body), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onChangeYear, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(stringResource(R.string.ob_u13_change))
        }
    }
}
