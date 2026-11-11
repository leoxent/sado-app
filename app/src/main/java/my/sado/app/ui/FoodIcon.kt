package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.sado.app.R
import my.sado.app.data.FoodIcons
import my.sado.app.data.FoodItem

private val foodDrawables = mapOf(
    "pinggan" to R.drawable.food_pinggan,
    "nasi_lemak" to R.drawable.food_nasi_lemak,
    "roti_canai" to R.drawable.food_roti_canai,
    "mee_goreng" to R.drawable.food_mee_goreng,
    "nasi_goreng" to R.drawable.food_nasi_goreng,
    "char_kuey_teow" to R.drawable.food_char_kuey_teow,
    "laksa" to R.drawable.food_laksa,
    "satay" to R.drawable.food_satay,
    "rendang" to R.drawable.food_rendang,
    "ayam_masak_merah" to R.drawable.food_ayam_masak_merah,
    "nasi_ayam" to R.drawable.food_nasi_ayam,
    "nasi_campur" to R.drawable.food_nasi_campur,
    "murtabak" to R.drawable.food_murtabak,
    "chapati" to R.drawable.food_chapati,
    "thosai" to R.drawable.food_thosai,
    "kuih" to R.drawable.food_kuih,
    "pisang_goreng" to R.drawable.food_pisang_goreng,
    "nasi_kerabu" to R.drawable.food_nasi_kerabu,
    "nasi_dagang" to R.drawable.food_nasi_dagang,
    "nasi_briyani" to R.drawable.food_nasi_briyani,
    "nasi_kukus" to R.drawable.food_nasi_kukus,
    "wantan_mee" to R.drawable.food_wantan_mee,
    "mee_rebus" to R.drawable.food_mee_rebus,
    "lontong" to R.drawable.food_lontong,
    "soto" to R.drawable.food_soto,
    "ketupat" to R.drawable.food_ketupat,
    "roti_john" to R.drawable.food_roti_john,
    "popiah" to R.drawable.food_popiah,
    "rojak" to R.drawable.food_rojak,
    "asam_pedas" to R.drawable.food_asam_pedas,
    "sup_tulang" to R.drawable.food_sup_tulang,
    "ayam_penyet" to R.drawable.food_ayam_penyet,
    "nasi_impit" to R.drawable.food_nasi_impit,
    "roti_bakar" to R.drawable.food_roti_bakar,
    "telur_separuh" to R.drawable.food_telur_separuh,
    "cereal" to R.drawable.food_cereal,
    "pancake" to R.drawable.food_pancake,
    "waffle" to R.drawable.food_waffle,
    "sandwich" to R.drawable.food_sandwich,
    "pau" to R.drawable.food_pau,
    "dim_sum" to R.drawable.food_dim_sum,
    "karipap" to R.drawable.food_karipap,
    "apam_balik" to R.drawable.food_apam_balik,
    "keropok_lekor" to R.drawable.food_keropok_lekor,
    "lemang" to R.drawable.food_lemang,
    "bubur" to R.drawable.food_bubur,
    "mi_segera" to R.drawable.food_mi_segera,
    "onde_onde" to R.drawable.food_onde_onde,
    "croissant" to R.drawable.food_croissant,
    "ayam_goreng" to R.drawable.food_ayam_goreng,
    "dada_ayam" to R.drawable.food_dada_ayam,
    "peha_ayam" to R.drawable.food_peha_ayam,
    "steak" to R.drawable.food_steak,
    "ikan_goreng" to R.drawable.food_ikan_goreng,
    "ikan_bakar" to R.drawable.food_ikan_bakar,
    "udang" to R.drawable.food_udang,
    "telur_rebus" to R.drawable.food_telur_rebus,
    "telur_goreng" to R.drawable.food_telur_goreng,
    "telur_dadar" to R.drawable.food_telur_dadar,
    "tauhu" to R.drawable.food_tauhu,
    "tempe" to R.drawable.food_tempe,
    "tuna" to R.drawable.food_tuna,
    "sardin" to R.drawable.food_sardin,
    "kambing" to R.drawable.food_kambing,
    "nugget" to R.drawable.food_nugget,
    "nasi_putih" to R.drawable.food_nasi_putih,
    "nasi_perang" to R.drawable.food_nasi_perang,
    "roti" to R.drawable.food_roti,
    "oat" to R.drawable.food_oat,
    "kentang" to R.drawable.food_kentang,
    "keledek" to R.drawable.food_keledek,
    "mi_sup" to R.drawable.food_mi_sup,
    "jagung" to R.drawable.food_jagung,
    "salad" to R.drawable.food_salad,
    "brokoli" to R.drawable.food_brokoli,
    "sayur" to R.drawable.food_sayur,
    "pisang" to R.drawable.food_pisang,
    "epal" to R.drawable.food_epal,
    "betik" to R.drawable.food_betik,
    "tembikai" to R.drawable.food_tembikai,
    "durian" to R.drawable.food_durian,
    "mangga" to R.drawable.food_mangga,
    "oren" to R.drawable.food_oren,
    "anggur" to R.drawable.food_anggur,
    "nanas" to R.drawable.food_nanas,
    "kurma" to R.drawable.food_kurma,
    "teh_tarik" to R.drawable.food_teh_tarik,
    "kopi" to R.drawable.food_kopi,
    "milo" to R.drawable.food_milo,
    "air" to R.drawable.food_air,
    "susu" to R.drawable.food_susu,
    "shake" to R.drawable.food_shake,
    "soya" to R.drawable.food_soya,
    "soda" to R.drawable.food_soda,
    "jus_oren" to R.drawable.food_jus_oren,
    "sirap_bandung" to R.drawable.food_sirap_bandung,
    "teh_o_limau" to R.drawable.food_teh_o_limau,
    "kopi_ais" to R.drawable.food_kopi_ais,
    "bubble_tea" to R.drawable.food_bubble_tea,
    "kelapa" to R.drawable.food_kelapa,
    "air_tebu" to R.drawable.food_air_tebu,
    "smoothie" to R.drawable.food_smoothie,
    "matcha" to R.drawable.food_matcha,
    "energy_drink" to R.drawable.food_energy_drink,
    "isotonik" to R.drawable.food_isotonik,
    "yogurt" to R.drawable.food_yogurt,
    "kacang" to R.drawable.food_kacang,
    "coklat" to R.drawable.food_coklat,
    "biskut" to R.drawable.food_biskut,
    "cendol" to R.drawable.food_cendol,
    "ais_kacang" to R.drawable.food_ais_kacang,
    "aiskrim" to R.drawable.food_aiskrim,
    "puding" to R.drawable.food_puding,
    "kek" to R.drawable.food_kek,
    "donut" to R.drawable.food_donut,
    "protein_bar" to R.drawable.food_protein_bar,
    "parfait" to R.drawable.food_parfait,
    "peanut_butter" to R.drawable.food_peanut_butter,
    "burger" to R.drawable.food_burger,
    "pizza" to R.drawable.food_pizza,
    "meal_prep" to R.drawable.food_meal_prep,
    "avocado_toast" to R.drawable.food_avocado_toast,
    "fries" to R.drawable.food_fries,
    "spaghetti" to R.drawable.food_spaghetti,
    "chicken_chop" to R.drawable.food_chicken_chop,
    "fish_chips" to R.drawable.food_fish_chips,
    "shawarma" to R.drawable.food_shawarma,
    "sushi" to R.drawable.food_sushi,
    "sambal" to R.drawable.food_sambal,
    "sotong_sambal" to R.drawable.food_sotong_sambal,
    "kubis_goreng" to R.drawable.food_kubis_goreng,
    "sup_lembu" to R.drawable.food_sup_lembu,
    "kari_ayam" to R.drawable.food_kari_ayam,
    "masak_lemak_ikan" to R.drawable.food_masak_lemak_ikan,
    "daging_masak_hitam" to R.drawable.food_daging_masak_hitam,
    "ayam_masak_kicap" to R.drawable.food_ayam_masak_kicap,
    "telur_sambal" to R.drawable.food_telur_sambal,
    "ikan_bilis" to R.drawable.food_ikan_bilis,
    "sayur_lemak" to R.drawable.food_sayur_lemak,
    "kangkung" to R.drawable.food_kangkung,
    "taugeh" to R.drawable.food_taugeh,
    "terung" to R.drawable.food_terung,
    "ulam" to R.drawable.food_ulam,
    "ikan_masin" to R.drawable.food_ikan_masin,
    "maggi_kari" to R.drawable.food_maggi_kari,
    "tomyam" to R.drawable.food_tomyam,
    "sup_ayam" to R.drawable.food_sup_ayam,
    "bihun_goreng" to R.drawable.food_bihun_goreng,
    "kuey_teow_sup" to R.drawable.food_kuey_teow_sup,
    "mee_kari" to R.drawable.food_mee_kari,
    "mee_bandung" to R.drawable.food_mee_bandung,
    "mee_hailam" to R.drawable.food_mee_hailam,
    "nasi_tomato" to R.drawable.food_nasi_tomato,
    "nasi_minyak" to R.drawable.food_nasi_minyak,
    "nasi_pattaya" to R.drawable.food_nasi_pattaya,
    "nasi_paprik" to R.drawable.food_nasi_paprik,
    "yong_tau_foo" to R.drawable.food_yong_tau_foo,
    "ramen" to R.drawable.food_ramen,
    "bubur_kacang" to R.drawable.food_bubur_kacang,
    "sotong_goreng" to R.drawable.food_sotong_goreng,
    "roti_jala" to R.drawable.food_roti_jala,
    "roti_tisu" to R.drawable.food_roti_tisu,
    "idli" to R.drawable.food_idli,
    "putu_mayam" to R.drawable.food_putu_mayam,
    "cucur_udang" to R.drawable.food_cucur_udang,
    "seri_muka" to R.drawable.food_seri_muka,
    "bahulu" to R.drawable.food_bahulu,
    "kuih_keria" to R.drawable.food_kuih_keria,
    "cekodok" to R.drawable.food_cekodok,
    "pengat_pisang" to R.drawable.food_pengat_pisang,
    "kerepek_pisang" to R.drawable.food_kerepek_pisang,
    "keropok" to R.drawable.food_keropok,
    "sosej" to R.drawable.food_sosej,
    "french_toast" to R.drawable.food_french_toast,
    "muffin" to R.drawable.food_muffin,
    "popcorn" to R.drawable.food_popcorn,
)

/** The cartoon thumbnail for a food; decorative, the name beside it says what it is. */
@Composable
fun FoodIcon(id: String, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(foodDrawables[id] ?: R.drawable.food_pinggan), contentDescription = null, modifier = modifier.size(size))
}

@Composable
fun FoodIcon(item: FoodItem, size: Dp, modifier: Modifier = Modifier) = FoodIcon(FoodIcons.of(item), size, modifier)

/** The thumbnail in the confirm list: tap to pick another, with a small pencil so it reads as editable. */
@Composable
fun FoodIconButton(item: FoodItem, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size + 4.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = "Tukar gambar", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FoodIcon(item, size)
        Box(
            Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(SadoColors.Surface2).border(1.dp, SadoColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Edit, contentDescription = null, tint = SadoColors.Text, modifier = Modifier.size(11.dp)) }
    }
}

/** Every food thumbnail in a grid; the current one is ringed in the accent. */
@Composable
fun FoodIconPicker(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { FoodIconPickerBody(current, onPick, onDismiss) }
}

@Composable
fun FoodIconPickerBody(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
        val tabs = listOf("Semua") + FoodIcons.groups
        var tab by remember { mutableStateOf(FoodIcons.all.firstOrNull { it.id == current }?.group?.takeIf { it in tabs } ?: "Semua") }
        val shown = if (tab == "Semua") FoodIcons.all else FoodIcons.all.filter { it.group == tab }
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
                .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pilih gambar", color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Gambar je yang berubah. Kalori tak terjejas.", color = SadoColors.TextMuted, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { g ->
                    val on = g == tab
                    Text(
                        g, color = if (on) SadoColors.OnAccent else SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) SadoColors.Accent else SadoColors.Surface2)
                            .clickable(role = Role.Tab) { tab = g }.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.id }) { ic ->
                    val on = ic.id == current
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) SadoColors.Accent.copy(alpha = 0.16f) else SadoColors.Surface2)
                            .border(if (on) 2.dp else 0.dp, if (on) SadoColors.Accent else SadoColors.Surface2, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onPick(ic.id) }.padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FoodIcon(ic.id, 48.dp)
                        Text(ic.label, color = SadoColors.Text, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Tutup", color = SadoColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onDismiss).padding(12.dp))
            }
        }
}
