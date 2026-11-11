package my.sado.app.data

/**
 * Picks a cartoon thumbnail for a food by its name. Each icon lists whole-word keywords (Malay and English);
 * the longest match wins, with a small edge for the word the name starts with ("Nasi lemak ayam goreng" is
 * nasi lemak). Nothing matched: the empty plate. A keyword starting with "=" only matches the whole name.
 * A keyword lives on one icon only; on a tie the earlier icon wins.
 */
object FoodIcons {
    class Icon(val id: String, val label: String, val group: String, val keywords: List<String>)

    const val FALLBACK = "pinggan"

    /** Tabs in the picker, in order; "Semua" shows every icon. */
    val groups = listOf("Malaysia", "Sarapan", "Protein", "Karbo", "Buah & sayur", "Minuman", "Manis & snek", "Western & gym")

    private const val MY = "Malaysia"
    private const val BF = "Sarapan"
    private const val PR = "Protein"
    private const val CB = "Karbo"
    private const val FV = "Buah & sayur"
    private const val DR = "Minuman"
    private const val SW = "Manis & snek"
    private const val WG = "Western & gym"

    val all = listOf(
        // Malaysian dishes
        Icon("nasi_lemak", "Nasi lemak", MY, listOf("nasi lemak")),
        Icon("roti_canai", "Roti canai", MY, listOf("roti canai", "roti telur", "roti kosong", "roti planta", "roti bawang", "roti sardin", "roti pisang")),
        Icon("mee_goreng", "Mee goreng", MY, listOf("mee goreng", "mi goreng", "maggi goreng", "mee", "mi", "noodle", "noodles", "fried noodles", "mee mamak")),
        Icon("nasi_goreng", "Nasi goreng", MY, listOf("nasi goreng", "fried rice", "nasi goreng kampung", "nasi goreng cina")),
        Icon("char_kuey_teow", "Kuey teow", MY, listOf("char kuey teow", "kuey teow", "kway teow", "ckt", "kuetiau", "kuey teow goreng")),
        Icon("laksa", "Laksa", MY, listOf("laksa", "asam laksa", "laksa johor", "laksam")),
        Icon("satay", "Satay", MY, listOf("satay", "sate")),
        Icon("rendang", "Rendang", MY, listOf("rendang", "daging", "beef", "lembu", "daging masak", "paru", "paru goreng", "hati", "limpa", "babat", "perut", "kerutuk")),
        Icon("ayam_masak_merah", "Ayam masak", MY, listOf("ayam masak merah", "masak merah", "ayam masak", "ayam kurma", "ayam masak kurma")),
        Icon("nasi_ayam", "Nasi ayam", MY, listOf("nasi ayam", "chicken rice", "nasi ayam hainan")),
        Icon("nasi_campur", "Nasi campur", MY, listOf("nasi campur", "nasi kandar", "nasi berlauk", "economy rice", "nasi bujang", "lauk")),
        Icon("murtabak", "Murtabak", MY, listOf("murtabak")),
        Icon("chapati", "Chapati", MY, listOf("chapati", "capati", "tortilla", "dhal", "dal", "naan", "nan")),
        Icon("thosai", "Thosai", MY, listOf("thosai", "tosai", "dosa", "appam", "apam")),
        Icon("kuih", "Kuih", MY, listOf("kuih", "kueh", "kuih muih", "kuih lapis", "kuih koci")),
        Icon("pisang_goreng", "Goreng pisang", MY, listOf("pisang goreng", "goreng pisang", "fritter", "fritters", "cempedak goreng")),
        Icon("nasi_kerabu", "Nasi kerabu", MY, listOf("nasi kerabu", "nasi biru")),
        Icon("nasi_dagang", "Nasi dagang", MY, listOf("nasi dagang")),
        Icon("nasi_briyani", "Nasi briyani", MY, listOf("nasi briyani", "briyani", "biryani", "nasi beriani", "beriani", "nasi mandy", "mandy", "nasi arab")),
        Icon("nasi_kukus", "Nasi kukus", MY, listOf("nasi kukus", "nasi kukus ayam")),
        Icon("wantan_mee", "Wantan mee", MY, listOf("wantan mee", "wanton mee", "wantan", "wanton", "mee kicap", "kolo mee", "pan mee", "char siu", "chasiu")),
        Icon("mee_rebus", "Mee rebus", MY, listOf("mee rebus", "mi rebus", "mee jawa")),
        Icon("lontong", "Lontong", MY, listOf("lontong", "sayur lodeh", "lodeh")),
        Icon("soto", "Soto", MY, listOf("soto", "soto ayam", "mee soto", "bihun soto")),
        Icon("ketupat", "Ketupat", MY, listOf("ketupat", "kuah kacang")),
        Icon("roti_john", "Roti john", MY, listOf("roti john")),
        Icon("popiah", "Popiah", MY, listOf("popiah", "spring roll", "spring rolls")),
        Icon("rojak", "Rojak", MY, listOf("rojak", "rojak buah", "pasembur", "rojak mamak", "cendawan goreng")),
        Icon("asam_pedas", "Asam pedas", MY, listOf("asam pedas", "ikan asam pedas", "masak asam", "ikan masak")),
        Icon("sup_tulang", "Sup tulang", MY, listOf("sup tulang", "sup kambing", "sup gearbox", "sup ekor", "oxtail")),
        Icon("ayam_penyet", "Ayam penyet", MY, listOf("ayam penyet", "penyet", "ayam geprek", "geprek", "ikan penyet")),
        Icon("nasi_impit", "Nasi impit", MY, listOf("nasi impit", "impit")),
        // breakfast and kopitiam
        Icon("roti_bakar", "Roti bakar", BF, listOf("roti bakar", "toast", "kaya", "roti kaya", "kaya toast", "roti bakar kaya")),
        Icon("telur_separuh", "Telur separuh masak", BF, listOf("telur separuh masak", "half boiled", "half boiled egg", "telur setengah masak", "soft boiled")),
        Icon("cereal", "Cereal", BF, listOf("cereal", "bijirin", "corn flakes", "cornflakes", "koko krunch", "muesli")),
        Icon("pancake", "Pancake", BF, listOf("pancake", "pancakes", "lempeng", "hotcake")),
        Icon("waffle", "Waffle", BF, listOf("waffle", "waffles", "wafel")),
        Icon("sandwich", "Sandwich", BF, listOf("sandwich", "sandwic", "roti sandwich", "sandwich telur", "egg sandwich", "club sandwich", "sub")),
        Icon("pau", "Pau", BF, listOf("pau", "bun", "bao", "pau ayam", "pau kacang", "char siu bao")),
        Icon("dim_sum", "Dim sum", BF, listOf("dim sum", "dimsum", "siu mai", "siew mai", "har gow", "dumpling", "dumplings", "gyoza")),
        Icon("karipap", "Karipap", BF, listOf("karipap", "curry puff", "epok epok", "pastry")),
        Icon("apam_balik", "Apam balik", BF, listOf("apam balik", "martabak manis", "terang bulan")),
        Icon("keropok_lekor", "Keropok lekor", BF, listOf("keropok lekor", "lekor")),
        Icon("lemang", "Lemang", BF, listOf("lemang", "pulut", "nasi pulut", "pulut kuning")),
        Icon("bubur", "Bubur", BF, listOf("bubur", "bubur ayam", "porridge", "congee", "bubur nasi", "bubur lambuk")),
        Icon("mi_segera", "Mi segera", BF, listOf("mi segera", "mee segera", "maggi cup", "instant noodle", "instant noodles", "cup noodle", "cup noodles", "ramyeon", "samyang", "indomie")),
        Icon("onde_onde", "Onde-onde", BF, listOf("onde onde", "buah melaka", "klepon")),
        Icon("croissant", "Croissant", BF, listOf("croissant", "pastri", "danish")),
        // protein
        Icon("ayam_goreng", "Ayam goreng", PR, listOf("ayam goreng", "fried chicken", "ayam", "chicken", "kfc", "drumstick", "ayam goreng berempah")),
        Icon("dada_ayam", "Dada ayam", PR, listOf("dada ayam", "chicken breast", "ayam bakar", "grilled chicken", "ayam panggang", "ayam rebus", "ayam grill")),
        Icon("peha_ayam", "Peha ayam", PR, listOf("peha ayam", "chicken thigh", "thigh", "peha")),
        Icon("steak", "Steak", PR, listOf("steak", "stik", "daging panggang", "lamb chop", "daging bakar")),
        Icon("ikan_goreng", "Ikan goreng", PR, listOf("ikan goreng", "ikan", "fish", "kembung", "keli", "tilapia", "siakap", "selar", "cencaru")),
        Icon("ikan_bakar", "Ikan bakar", PR, listOf("ikan bakar", "salmon", "grilled fish", "fish fillet", "dory", "ikan panggang", "ikan kukus", "steamed fish")),
        Icon("udang", "Udang", PR, listOf("udang", "prawn", "prawns", "shrimp", "seafood", "ketam", "crab", "kerang", "udang sambal", "sambal udang")),
        Icon("telur_rebus", "Telur rebus", PR, listOf("telur rebus", "boiled egg", "telur", "egg", "eggs", "putih telur", "egg white", "hard boiled")),
        Icon("telur_goreng", "Telur goreng", PR, listOf("telur goreng", "telur mata", "telur mata kerbau", "fried egg", "sunny side up")),
        Icon("telur_dadar", "Telur dadar", PR, listOf("telur dadar", "omelette", "omelet", "scrambled egg", "scrambled eggs", "telur hancur", "telur bistik")),
        Icon("tauhu", "Tauhu", PR, listOf("tauhu", "tofu", "tahu", "tauhu goreng", "tauhu sumbat", "fucuk")),
        Icon("tempe", "Tempe", PR, listOf("tempe", "tempeh", "tempe goreng")),
        Icon("tuna", "Tuna", PR, listOf("tuna", "tuna can", "tin tuna")),
        Icon("sardin", "Sardin", PR, listOf("sardin", "sardine", "sardines", "sambal sardin")),
        Icon("kambing", "Kambing", PR, listOf("kambing", "mutton", "lamb", "kari kambing", "gulai kambing", "kambing golek")),
        Icon("nugget", "Nugget", PR, listOf("nugget", "nuggets", "meatball", "meatballs", "bebola", "bebola ikan", "fishball", "fish ball")),
        // carbs
        Icon("nasi_putih", "Nasi putih", CB, listOf("nasi putih", "nasi", "rice", "white rice", "nasi kosong")),
        Icon("nasi_perang", "Nasi perang", CB, listOf("nasi perang", "brown rice", "beras perang", "quinoa", "nasi merah")),
        Icon("roti", "Roti", CB, listOf("roti", "bread", "gardenia", "roti gandum", "wholemeal", "roti putih", "massimo")),
        Icon("oat", "Oat", CB, listOf("oat", "oats", "oatmeal", "quaker", "overnight oats")),
        Icon("kentang", "Kentang", CB, listOf("kentang", "potato", "potatoes", "kentang rebus", "mashed potato", "kentang lenyek")),
        Icon("keledek", "Keledek", CB, listOf("keledek", "ubi", "sweet potato", "ubi keledek", "ubi kayu")),
        Icon("mi_sup", "Mee sup", CB, listOf("mee sup", "mi sup", "pho", "bakso", "mee kuah")),
        Icon("jagung", "Jagung", CB, listOf("jagung", "corn", "jagung rebus", "sweet corn")),
        // fruit and veg
        Icon("salad", "Salad", FV, listOf("salad", "tomato", "tomatoes", "kerabu")),
        Icon("brokoli", "Brokoli", FV, listOf("brokoli", "broccoli", "bunga kobis", "cauliflower")),
        Icon("sayur", "Sayur", FV, listOf("sayur", "sayur campur", "sawi", "bayam", "vegetable", "vegetables", "veggies", "kailan", "bendi", "kacang panjang", "sayur goreng")),
        Icon("pisang", "Pisang", FV, listOf("pisang", "banana", "bananas")),
        Icon("epal", "Epal", FV, listOf("epal", "apple", "pear", "pir")),
        Icon("betik", "Betik", FV, listOf("betik", "papaya")),
        Icon("tembikai", "Tembikai", FV, listOf("tembikai", "watermelon", "buah", "fruit", "fruits", "strawberi", "strawberry", "jambu", "guava", "buah potong")),
        Icon("durian", "Durian", FV, listOf("durian", "cempedak", "nangka", "jackfruit")),
        Icon("mangga", "Mangga", FV, listOf("mangga", "mango", "mempelam")),
        Icon("oren", "Oren", FV, listOf("oren", "orange", "limau mandarin", "mandarin", "tangerine")),
        Icon("anggur", "Anggur", FV, listOf("anggur", "grapes", "grape", "kismis", "raisins", "blueberry", "blueberries")),
        Icon("nanas", "Nanas", FV, listOf("nanas", "pineapple")),
        Icon("kurma", "Kurma", FV, listOf("kurma", "dates", "buah kurma", "tamar")),
        // drinks
        Icon("teh_tarik", "Teh tarik", DR, listOf("teh tarik", "teh", "tea", "teh ais", "chai", "air teh", "teh susu", "teh c", "teh panas")),
        Icon("kopi", "Kopi", DR, listOf("kopi", "coffee", "latte", "americano", "espresso", "cappuccino", "nescafe", "kopi o", "air kopi", "mocha", "kopi panas", "white coffee")),
        Icon("milo", "Milo", DR, listOf("milo", "air milo", "milo ais", "milo panas", "horlicks", "ovaltine", "coklat panas", "hot chocolate", "chocolate drink", "neslo", "milo dinosaur")),
        Icon("air", "Air kosong", DR, listOf("=air", "air kosong", "water", "plain water", "mineral water", "air mineral", "air suam", "ice water")),
        Icon("susu", "Susu", DR, listOf("susu", "milk", "fresh milk", "susu segar", "dutch lady", "susu full cream", "susu rendah lemak", "susu coklat", "chocolate milk")),
        Icon("shake", "Protein shake", DR, listOf("whey", "protein shake", "shake", "protein", "scoop", "mass gainer", "gainer", "casein", "isolate")),
        Icon("soya", "Susu soya", DR, listOf("soya", "soy milk", "air soya", "susu soya", "almond milk", "oat milk", "air barli", "barley")),
        Icon("soda", "Air bergas", DR, listOf("soda", "coke", "cola", "pepsi", "sprite", "fizzy", "air bergas", "minuman bergas", "air tin", "fanta")),
        Icon("jus_oren", "Jus", DR, listOf("jus", "juice", "jus oren", "orange juice", "lemonade", "jus epal", "jus tembikai", "jus mangga", "fresh orange")),
        Icon("sirap_bandung", "Sirap bandung", DR, listOf("sirap bandung", "bandung", "sirap", "air sirap", "rose syrup", "sirap ais", "air mawar")),
        Icon("teh_o_limau", "Teh o limau", DR, listOf("teh o", "teh o ais", "teh o limau", "teh o ais limau", "limau", "air limau", "lemon tea", "ice lemon tea", "limau ais", "teh o laici", "laici")),
        Icon("kopi_ais", "Kopi ais", DR, listOf("kopi ais", "iced coffee", "ice coffee", "kopi o ais", "iced latte", "ice latte", "cold brew")),
        Icon("bubble_tea", "Bubble tea", DR, listOf("bubble tea", "boba", "teh mutiara", "milk tea", "chatime", "tealive", "brown sugar")),
        Icon("kelapa", "Air kelapa", DR, listOf("air kelapa", "kelapa", "coconut", "coconut water", "kelapa muda")),
        Icon("air_tebu", "Air tebu", DR, listOf("air tebu", "tebu", "sugarcane", "sugar cane")),
        Icon("smoothie", "Smoothie", DR, listOf("smoothie", "smoothies", "jus buah campur", "fruit shake")),
        Icon("matcha", "Matcha", DR, listOf("matcha", "matcha latte", "green tea", "teh hijau")),
        Icon("energy_drink", "Minuman tenaga", DR, listOf("energy drink", "red bull", "monster", "livita", "minuman tenaga", "pre workout", "preworkout")),
        Icon("isotonik", "Isotonik", DR, listOf("isotonik", "isotonic", "100 plus", "100plus", "revive", "gatorade", "pocari", "sports drink", "air isotonik")),
        // sweets and snacks
        Icon("yogurt", "Yogurt", SW, listOf("yogurt", "yoghurt", "yakult", "dadih", "greek yogurt")),
        Icon("kacang", "Kacang", SW, listOf("kacang", "nuts", "peanut", "peanuts", "almond", "almonds", "cashew", "gajus", "kacang tanah", "mixed nuts", "walnut")),
        Icon("coklat", "Coklat", SW, listOf("coklat", "chocolate", "kitkat", "snickers", "candy", "gula gula", "beng beng", "cadbury", "dairy milk")),
        Icon("biskut", "Biskut", SW, listOf("crackers", "biskut", "biscuit", "biscuits", "cracker", "cookie", "cookies", "hup seng", "jacob", "marie", "biskut kering", "chipsmore")),
        Icon("cendol", "Cendol", SW, listOf("cendol", "chendol")),
        Icon("ais_kacang", "Ais kacang", SW, listOf("ais kacang", "abc", "air batu campur", "ice kacang", "bingsu")),
        Icon("aiskrim", "Aiskrim", SW, listOf("aiskrim", "ais krim", "ice cream", "icecream", "cone", "sundae", "gelato", "mcflurry")),
        Icon("puding", "Puding", SW, listOf("puding", "pudding", "caramel", "creme caramel", "puding roti", "agar agar", "jeli", "jelly")),
        Icon("kek", "Kek", SW, listOf("kek", "cake", "cheesecake", "kek batik", "brownie", "brownies", "kek coklat")),
        Icon("donut", "Donut", SW, listOf("donut", "doughnut", "donat")),
        Icon("protein_bar", "Protein bar", SW, listOf("protein bar", "bar protein", "granola bar", "energy bar", "cereal bar", "quest bar")),
        Icon("parfait", "Parfait", SW, listOf("parfait", "granola", "yogurt granola", "acai", "acai bowl")),
        Icon("peanut_butter", "Peanut butter", SW, listOf("peanut butter", "mentega kacang", "selai kacang", "nutella", "selai", "jem", "jam")),
        // western, fast food, gym meals
        Icon("burger", "Burger", WG, listOf("burger", "ramly", "mcd", "mcdonald", "big mac", "zinger", "cheeseburger")),
        Icon("pizza", "Pizza", WG, listOf("pizza", "domino", "pizza hut")),
        Icon("meal_prep", "Meal prep", WG, listOf("meal prep", "mealprep", "bekal", "bekal nasi", "lunch box", "bento")),
        Icon("avocado_toast", "Avocado toast", WG, listOf("avocado toast", "avocado", "avokado", "alpukat")),
        Icon("fries", "Kentang goreng", WG, listOf("fries", "french fries", "kentang goreng", "wedges", "potato wedges", "hash brown", "hashbrown")),
        Icon("spaghetti", "Spaghetti", WG, listOf("spaghetti", "pasta", "bolognese", "carbonara", "aglio olio", "macaroni", "mac and cheese", "lasagna", "lasagne")),
        Icon("chicken_chop", "Chicken chop", WG, listOf("chicken chop", "black pepper", "lamb chop sos", "grill chicken chop", "chop")),
        Icon("fish_chips", "Fish & chips", WG, listOf("fish and chips", "fish n chips", "fish chips", "fish & chips", "fish burger")),
        Icon("shawarma", "Shawarma", WG, listOf("shawarma", "kebab", "wrap", "burrito", "doner")),
        Icon("sushi", "Sushi", WG, listOf("sushi", "sashimi", "onigiri", "maki", "sushi king")),
        Icon(FALLBACK, "Lain-lain", "", emptyList()),
        // 0.26: lauk kampung, mi & sup, kuih & snek
        Icon("sambal", "Sambal", MY, listOf("sambal", "sambal tumis", "sambal merah", "sambal hijau", "chili paste")),
        Icon("sotong_sambal", "Sotong sambal", MY, listOf("sotong sambal", "sambal sotong", "sotong", "squid", "sotong masak", "sotong kicap")),
        Icon("kubis_goreng", "Kubis goreng", FV, listOf("kubis", "kubis goreng", "kobis", "kobis goreng", "cabbage", "sayur kubis")),
        Icon("sup_lembu", "Sup lembu", MY, listOf("sup lembu", "sup daging", "beef soup", "sup daging lembu")),
        Icon("kari_ayam", "Kari ayam", MY, listOf("kari ayam", "ayam kari", "chicken curry", "gulai ayam", "kari", "curry", "gulai", "kari daging", "kari kentang")),
        Icon("masak_lemak_ikan", "Masak lemak", MY, listOf("masak lemak", "masak lemak cili api", "ikan masak lemak", "lemak cili api", "gulai ikan", "ikan gulai", "kari ikan")),
        Icon("daging_masak_hitam", "Daging masak hitam", MY, listOf("daging masak hitam", "masak hitam", "daging kicap", "daging masak kicap")),
        Icon("ayam_masak_kicap", "Ayam kicap", MY, listOf("ayam kicap", "ayam masak kicap", "masak kicap", "kicap")),
        Icon("telur_sambal", "Telur sambal", MY, listOf("telur sambal", "sambal telur", "telur masak sambal", "telur balado", "telur masak")),
        Icon("ikan_bilis", "Ikan bilis", PR, listOf("ikan bilis", "bilis", "anchovy", "anchovies", "ikan bilis goreng", "sambal ikan bilis", "ikan bilis kacang")),
        Icon("sayur_lemak", "Sayur lemak", FV, listOf("sayur lemak", "sayur masak lemak", "masak lemak labu", "labu", "pumpkin", "masak lemak putih")),
        Icon("kangkung", "Kangkung", FV, listOf("kangkung", "kangkung belacan", "sayur kangkung", "kangkung goreng", "water spinach", "pucuk paku", "paku pakis")),
        Icon("taugeh", "Taugeh", FV, listOf("taugeh", "tauge", "taugeh goreng", "bean sprouts", "bean sprout")),
        Icon("terung", "Terung", FV, listOf("terung", "terung sambal", "sambal terung", "terung goreng", "eggplant", "brinjal", "terung bakar")),
        Icon("ulam", "Ulam", FV, listOf("ulam", "ulam ulaman", "ulam raja", "pegaga", "sambal belacan", "belacan", "timun", "cucumber")),
        Icon("ikan_masin", "Ikan masin", PR, listOf("ikan masin", "salted fish", "ikan kering", "ikan masin goreng", "gelama masin")),
        Icon("maggi_kari", "Maggi kari", MY, listOf("maggi kari", "maggie kari", "maggi", "maggie", "maggi sup", "maggie sup", "maggi tomyam", "mee maggi")),
        Icon("tomyam", "Tomyam", MY, listOf("tomyam", "tom yam", "tomyum", "tom yum", "tomyam campur", "tomyam seafood", "tomyam ayam")),
        Icon("sup_ayam", "Sup ayam", MY, listOf("sup ayam", "chicken soup", "sup sayur", "sup", "soup", "sup cendawan")),
        Icon("bihun_goreng", "Bihun goreng", MY, listOf("bihun goreng", "mihun goreng", "bihun", "mihun", "bee hoon", "meehoon", "bihun singapore")),
        Icon("kuey_teow_sup", "Kuey teow sup", MY, listOf("kuey teow sup", "kueh teow sup", "kuey teow soup", "kuey teow kungfu", "bihun sup", "mee sup ayam")),
        Icon("mee_kari", "Mee kari", MY, listOf("mee kari", "kari mee", "curry mee", "curry laksa", "mi kari")),
        Icon("mee_bandung", "Mee bandung", MY, listOf("mee bandung", "mi bandung", "mee bandung muar")),
        Icon("mee_hailam", "Mee hailam", MY, listOf("mee hailam", "mi hailam", "hokkien mee", "mee hokkien", "mee kungfu", "mee goreng basah")),
        Icon("nasi_tomato", "Nasi tomato", MY, listOf("nasi tomato", "nasi tomato ayam")),
        Icon("nasi_minyak", "Nasi minyak", MY, listOf("nasi minyak", "nasi hujan panas", "ghee rice")),
        Icon("nasi_pattaya", "Nasi pattaya", MY, listOf("nasi pattaya", "nasi pataya", "omurice")),
        Icon("nasi_paprik", "Nasi paprik", MY, listOf("nasi paprik", "ayam paprik", "paprik", "nasi goreng paprik", "paprik ayam", "paprik daging")),
        Icon("yong_tau_foo", "Yong tau foo", MY, listOf("yong tau foo", "yong tau fu", "ytf", "lok lok", "loklok")),
        Icon("ramen", "Ramen", CB, listOf("ramen", "udon", "soba", "japanese noodle", "japanese noodles")),
        Icon("bubur_kacang", "Bubur kacang", SW, listOf("bubur kacang", "bubur kacang hijau", "kacang hijau", "bubur pulut hitam", "pulut hitam", "bubur caca", "bubur jagung", "bubur gandum")),
        Icon("sotong_goreng", "Sotong goreng", MY, listOf("sotong goreng", "sotong goreng tepung", "sotong tepung", "calamari", "fried calamari", "squid rings")),
        Icon("roti_jala", "Roti jala", BF, listOf("roti jala")),
        Icon("roti_tisu", "Roti tisu", BF, listOf("roti tisu", "roti tissue", "roti tisu planta")),
        Icon("idli", "Idli", BF, listOf("idli", "idly")),
        Icon("putu_mayam", "Putu mayam", BF, listOf("putu mayam", "putu mayang", "idiyappam", "putu")),
        Icon("cucur_udang", "Cucur udang", SW, listOf("cucur udang", "cucur", "cucur bawang", "cucur sayur", "jemput udang")),
        Icon("seri_muka", "Seri muka", SW, listOf("seri muka", "kuih seri muka", "kuih talam", "talam")),
        Icon("bahulu", "Bahulu", SW, listOf("bahulu", "kuih bahulu", "baulu", "kuih baulu")),
        Icon("kuih_keria", "Kuih keria", SW, listOf("keria", "kuih keria", "donut keledek", "kuih keria gula")),
        Icon("cekodok", "Cekodok", SW, listOf("cekodok", "cekodok pisang", "jemput jemput", "jemput pisang", "cokodok")),
        Icon("pengat_pisang", "Pengat pisang", SW, listOf("pengat", "pengat pisang", "bubur pisang", "serawa")),
        Icon("kerepek_pisang", "Kerepek", SW, listOf("kerepek", "kerepek pisang", "banana chips", "kerepek ubi", "kerepek kentang", "potato chips", "kerepek tempe")),
        Icon("keropok", "Keropok", SW, listOf("keropok", "prawn crackers", "keropok udang", "keropok ikan", "keropok goreng")),
        Icon("sosej", "Sosej", BF, listOf("sosej", "sausage", "sausages", "hotdog", "hot dog", "frankfurter", "sosej ayam", "sosej goreng")),
        Icon("french_toast", "French toast", BF, listOf("french toast", "roti celup telur", "roti telur goreng", "roti goreng telur")),
        Icon("muffin", "Muffin", SW, listOf("muffin", "muffins", "cupcake", "cupcakes", "blueberry muffin")),
        Icon("popcorn", "Popcorn", SW, listOf("popcorn", "bertih jagung", "bertih")),
    )

    private val byId = all.associateBy { it.id }

    fun label(id: String) = byId[id]?.label ?: "Lain-lain"

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The icon this food shows: the one the user picked, else the best name match, else the plate. */
    fun of(item: FoodItem): String = item.icon?.takeIf { it in byId } ?: match(item.name)

    fun match(name: String): String {
        val n = norm(name)
        if (n.isEmpty()) return FALLBACK
        val padded = " $n "
        var best = FALLBACK
        var bestScore = 0
        for (icon in all) for (raw in icon.keywords) {
            val exact = raw.startsWith("=")
            val kw = norm(raw.removePrefix("="))
            val hit = if (exact) n == kw else padded.contains(" $kw ")
            if (!hit) continue
            val score = kw.length + (if (n.startsWith("$kw ") || n == kw) 3 else 0)
            if (score > bestScore) { best = icon.id; bestScore = score }
        }
        return best
    }
}
