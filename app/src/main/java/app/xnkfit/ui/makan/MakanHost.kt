package app.xnkfit.ui.makan

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/** The Makan tab: its own small stack of screens (home, pick food, week planner, shopping). Back goes up one level. */
@Composable
fun MakanHost(vm: MakanViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.route != MakanRoute.HOME) { vm.back() }
    when (state.route) {
        MakanRoute.HOME -> MakanScreen(state, vm, modifier)
        MakanRoute.PICK -> PickScreen(state, vm, modifier)
        MakanRoute.PLANNER -> PlannerScreen(state, vm, modifier)
        MakanRoute.SHOPPING -> ShoppingScreen(state, vm, modifier)
    }
}
