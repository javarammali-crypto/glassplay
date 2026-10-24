package com.glassplay.app.data

import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import kotlin.math.abs

/** A movie in a franchise, named the way IMDb (and so Cinemeta) names it. */
data class FranchiseMovie(val title: String, val year: Int)

data class Franchise(
    val id: String,
    val name: String,
    val tagline: String,
    /** Two colours for the card's gradient. */
    val colors: Pair<Long, Long>,
    val movies: List<FranchiseMovie>,
)

private fun movies(vararg items: Pair<String, Int>) = items.map { (t, y) -> FranchiseMovie(t, y) }

/** Big movie franchises, each in release order. */
val FRANCHISES: List<Franchise> = listOf(
    Franchise(
        "mcu", "Marvel Cinematic Universe", "Every Avengers saga film, from Iron Man on",
        0xFFB71C1C to 0xFF3E0A0A,
        movies(
            "Iron Man" to 2008, "The Incredible Hulk" to 2008, "Iron Man 2" to 2010, "Thor" to 2011,
            "Captain America: The First Avenger" to 2011, "The Avengers" to 2012, "Iron Man 3" to 2013,
            "Thor: The Dark World" to 2013, "Captain America: The Winter Soldier" to 2014,
            "Guardians of the Galaxy" to 2014, "Avengers: Age of Ultron" to 2015, "Ant-Man" to 2015,
            "Captain America: Civil War" to 2016, "Doctor Strange" to 2016, "Guardians of the Galaxy Vol. 2" to 2017,
            "Spider-Man: Homecoming" to 2017, "Thor: Ragnarok" to 2017, "Black Panther" to 2018,
            "Avengers: Infinity War" to 2018, "Ant-Man and the Wasp" to 2018, "Captain Marvel" to 2019,
            "Avengers: Endgame" to 2019, "Spider-Man: Far from Home" to 2019, "Black Widow" to 2021,
            "Shang-Chi and the Legend of the Ten Rings" to 2021, "Eternals" to 2021, "Spider-Man: No Way Home" to 2021,
            "Doctor Strange in the Multiverse of Madness" to 2022, "Thor: Love and Thunder" to 2022,
            "Black Panther: Wakanda Forever" to 2022, "Ant-Man and the Wasp: Quantumania" to 2023,
            "Guardians of the Galaxy Vol. 3" to 2023, "The Marvels" to 2023, "Deadpool & Wolverine" to 2024,
            "Captain America: Brave New World" to 2025, "Thunderbolts*" to 2025,
            "The Fantastic Four: First Steps" to 2025, "Spider-Man: Brand New Day" to 2026,
        ),
    ),
    Franchise(
        "dc", "DC Universe", "The DC Extended Universe and the new DCU",
        0xFF0D47A1 to 0xFF0A1A33,
        movies(
            "Man of Steel" to 2013, "Batman v Superman: Dawn of Justice" to 2016, "Suicide Squad" to 2016,
            "Wonder Woman" to 2017, "Justice League" to 2017, "Aquaman" to 2018, "Shazam!" to 2019,
            "Birds of Prey" to 2020, "Wonder Woman 1984" to 2020, "Zack Snyder's Justice League" to 2021,
            "The Suicide Squad" to 2021, "Black Adam" to 2022, "Shazam! Fury of the Gods" to 2023,
            "The Flash" to 2023, "Blue Beetle" to 2023, "Aquaman and the Lost Kingdom" to 2023,
            "Superman" to 2025, "Supergirl" to 2026,
        ),
    ),
    Franchise(
        "batman", "Batman", "The Dark Knight on the big screen",
        0xFF37474F to 0xFF0B0F12,
        movies(
            "Batman" to 1989, "Batman Returns" to 1992, "Batman Forever" to 1995, "Batman & Robin" to 1997,
            "Batman Begins" to 2005, "The Dark Knight" to 2008, "The Dark Knight Rises" to 2012, "The Batman" to 2022,
        ),
    ),
    Franchise(
        "spiderman", "Spider-Man", "Every live-action and Spider-Verse film",
        0xFFD32F2F to 0xFF0D2A6B,
        movies(
            "Spider-Man" to 2002, "Spider-Man 2" to 2004, "Spider-Man 3" to 2007, "The Amazing Spider-Man" to 2012,
            "The Amazing Spider-Man 2" to 2014, "Spider-Man: Homecoming" to 2017, "Spider-Man: Into the Spider-Verse" to 2018,
            "Spider-Man: Far from Home" to 2019, "Spider-Man: No Way Home" to 2021,
            "Spider-Man: Across the Spider-Verse" to 2023, "Spider-Man: Brand New Day" to 2026,
        ),
    ),
    Franchise(
        "xmen", "X-Men", "Mutants, Wolverine and Deadpool",
        0xFFF9A825 to 0xFF1A237E,
        movies(
            "X-Men" to 2000, "X2" to 2003, "X-Men: The Last Stand" to 2006, "X-Men Origins: Wolverine" to 2009,
            "X-Men: First Class" to 2011, "The Wolverine" to 2013, "X-Men: Days of Future Past" to 2014,
            "Deadpool" to 2016, "X-Men: Apocalypse" to 2016, "Logan" to 2017, "Deadpool 2" to 2018,
            "Dark Phoenix" to 2019, "The New Mutants" to 2020,
        ),
    ),
    Franchise(
        "wizarding", "Harry Potter & Wizarding World", "Hogwarts, Fantastic Beasts and more",
        0xFF5D4037 to 0xFF1B1209,
        movies(
            "Harry Potter and the Sorcerer's Stone" to 2001, "Harry Potter and the Chamber of Secrets" to 2002,
            "Harry Potter and the Prisoner of Azkaban" to 2004, "Harry Potter and the Goblet of Fire" to 2005,
            "Harry Potter and the Order of the Phoenix" to 2007, "Harry Potter and the Half-Blood Prince" to 2009,
            "Harry Potter and the Deathly Hallows: Part 1" to 2010, "Harry Potter and the Deathly Hallows: Part 2" to 2011,
            "Fantastic Beasts and Where to Find Them" to 2016, "Fantastic Beasts: The Crimes of Grindelwald" to 2018,
            "Fantastic Beasts: The Secrets of Dumbledore" to 2022,
        ),
    ),
    Franchise(
        "starwars", "Star Wars", "A long time ago in a galaxy far, far away",
        0xFF263238 to 0xFF000000,
        movies(
            "Star Wars: Episode IV - A New Hope" to 1977, "Star Wars: Episode V - The Empire Strikes Back" to 1980,
            "Star Wars: Episode VI - Return of the Jedi" to 1983, "Star Wars: Episode I - The Phantom Menace" to 1999,
            "Star Wars: Episode II - Attack of the Clones" to 2002, "Star Wars: Episode III - Revenge of the Sith" to 2005,
            "Star Wars: Episode VII - The Force Awakens" to 2015, "Rogue One: A Star Wars Story" to 2016,
            "Star Wars: Episode VIII - The Last Jedi" to 2017, "Solo: A Star Wars Story" to 2018,
            "Star Wars: Episode IX - The Rise of Skywalker" to 2019, "The Mandalorian and Grogu" to 2026,
        ),
    ),
    Franchise(
        "middleearth", "The Lord of the Rings", "The Lord of the Rings and The Hobbit",
        0xFF8D6E63 to 0xFF1F1A12,
        movies(
            "The Lord of the Rings: The Fellowship of the Ring" to 2001, "The Lord of the Rings: The Two Towers" to 2002,
            "The Lord of the Rings: The Return of the King" to 2003, "The Hobbit: An Unexpected Journey" to 2012,
            "The Hobbit: The Desolation of Smaug" to 2013, "The Hobbit: The Battle of the Five Armies" to 2014,
            "The Lord of the Rings: The War of the Rohirrim" to 2024,
        ),
    ),
    Franchise(
        "bond", "James Bond 007", "Every official Bond film",
        0xFF424242 to 0xFF0A0A0A,
        movies(
            "Dr. No" to 1962, "From Russia with Love" to 1963, "Goldfinger" to 1964, "Thunderball" to 1965,
            "You Only Live Twice" to 1967, "On Her Majesty's Secret Service" to 1969, "Diamonds Are Forever" to 1971,
            "Live and Let Die" to 1973, "The Man with the Golden Gun" to 1974, "The Spy Who Loved Me" to 1977,
            "Moonraker" to 1979, "For Your Eyes Only" to 1981, "Octopussy" to 1983, "A View to a Kill" to 1985,
            "The Living Daylights" to 1987, "Licence to Kill" to 1989, "GoldenEye" to 1995,
            "Tomorrow Never Dies" to 1997, "The World Is Not Enough" to 1999, "Die Another Day" to 2002,
            "Casino Royale" to 2006, "Quantum of Solace" to 2008, "Skyfall" to 2012, "Spectre" to 2015,
            "No Time to Die" to 2021,
        ),
    ),
    Franchise(
        "fast", "Fast & Furious", "Family, cars and impossible stunts",
        0xFFEF6C00 to 0xFF2B1300,
        movies(
            "The Fast and the Furious" to 2001, "2 Fast 2 Furious" to 2003, "The Fast and the Furious: Tokyo Drift" to 2006,
            "Fast & Furious" to 2009, "Fast Five" to 2011, "Fast & Furious 6" to 2013, "Furious 7" to 2015,
            "The Fate of the Furious" to 2017, "Fast & Furious Presents: Hobbs & Shaw" to 2019, "F9" to 2021,
            "Fast X" to 2023,
        ),
    ),
    Franchise(
        "mi", "Mission: Impossible", "Ethan Hunt's impossible missions",
        0xFF455A64 to 0xFF101518,
        movies(
            "Mission: Impossible" to 1996, "Mission: Impossible 2" to 2000, "Mission: Impossible III" to 2006,
            "Mission: Impossible - Ghost Protocol" to 2011, "Mission: Impossible - Rogue Nation" to 2015,
            "Mission: Impossible - Fallout" to 2018, "Mission: Impossible - Dead Reckoning Part One" to 2023,
            "Mission: Impossible - The Final Reckoning" to 2025,
        ),
    ),
    Franchise(
        "johnwick", "John Wick", "The Baba Yaga and his world",
        0xFF4A148C to 0xFF0E0418,
        movies(
            "John Wick" to 2014, "John Wick: Chapter 2" to 2017, "John Wick: Chapter 3 - Parabellum" to 2019,
            "John Wick: Chapter 4" to 2023, "Ballerina" to 2025,
        ),
    ),
    Franchise(
        "jurassic", "Jurassic Park", "Jurassic Park and Jurassic World",
        0xFF2E7D32 to 0xFF0A1F0B,
        movies(
            "Jurassic Park" to 1993, "The Lost World: Jurassic Park" to 1997, "Jurassic Park III" to 2001,
            "Jurassic World" to 2015, "Jurassic World: Fallen Kingdom" to 2018, "Jurassic World Dominion" to 2022,
            "Jurassic World Rebirth" to 2025,
        ),
    ),
    Franchise(
        "transformers", "Transformers", "Autobots versus Decepticons",
        0xFF1565C0 to 0xFF3B0A0A,
        movies(
            "Transformers" to 2007, "Transformers: Revenge of the Fallen" to 2009, "Transformers: Dark of the Moon" to 2011,
            "Transformers: Age of Extinction" to 2014, "Transformers: The Last Knight" to 2017, "Bumblebee" to 2018,
            "Transformers: Rise of the Beasts" to 2023, "Transformers One" to 2024,
        ),
    ),
    Franchise(
        "matrix", "The Matrix", "Take the red pill",
        0xFF1B5E20 to 0xFF000000,
        movies("The Matrix" to 1999, "The Matrix Reloaded" to 2003, "The Matrix Revolutions" to 2003, "The Matrix Resurrections" to 2021),
    ),
    Franchise(
        "pirates", "Pirates of the Caribbean", "Captain Jack Sparrow's adventures",
        0xFF00695C to 0xFF06201C,
        movies(
            "Pirates of the Caribbean: The Curse of the Black Pearl" to 2003, "Pirates of the Caribbean: Dead Man's Chest" to 2006,
            "Pirates of the Caribbean: At World's End" to 2007, "Pirates of the Caribbean: On Stranger Tides" to 2011,
            "Pirates of the Caribbean: Dead Men Tell No Tales" to 2017,
        ),
    ),
    Franchise(
        "avatar", "Avatar", "Return to Pandora",
        0xFF0277BD to 0xFF021C2E,
        movies("Avatar" to 2009, "Avatar: The Way of Water" to 2022, "Avatar: Fire and Ash" to 2025),
    ),
    Franchise(
        "dune", "Dune", "The spice must flow",
        0xFFE65100 to 0xFF2A1200,
        movies("Dune" to 2021, "Dune: Part Two" to 2024),
    ),
    Franchise(
        "hungergames", "The Hunger Games", "May the odds be ever in your favour",
        0xFFBF360C to 0xFF220A02,
        movies(
            "The Hunger Games" to 2012, "The Hunger Games: Catching Fire" to 2013, "The Hunger Games: Mockingjay - Part 1" to 2014,
            "The Hunger Games: Mockingjay - Part 2" to 2015, "The Hunger Games: The Ballad of Songbirds & Snakes" to 2023,
        ),
    ),
    Franchise(
        "twilight", "The Twilight Saga", "Bella, Edward and Jacob",
        0xFF546E7A to 0xFF0D1216,
        movies(
            "Twilight" to 2008, "The Twilight Saga: New Moon" to 2009, "The Twilight Saga: Eclipse" to 2010,
            "The Twilight Saga: Breaking Dawn - Part 1" to 2011, "The Twilight Saga: Breaking Dawn - Part 2" to 2012,
        ),
    ),
    Franchise(
        "conjuring", "The Conjuring Universe", "The Warrens' cases and the evil they found",
        0xFF3E2723 to 0xFF000000,
        movies(
            "The Conjuring" to 2013, "Annabelle" to 2014, "The Conjuring 2" to 2016, "Annabelle: Creation" to 2017,
            "The Nun" to 2018, "The Curse of La Llorona" to 2019, "Annabelle Comes Home" to 2019,
            "The Conjuring: The Devil Made Me Do It" to 2021, "The Nun II" to 2023, "The Conjuring: Last Rites" to 2025,
        ),
    ),
    Franchise(
        "monsterverse", "MonsterVerse", "Godzilla and King Kong",
        0xFF00838F to 0xFF041A1C,
        movies(
            "Godzilla" to 2014, "Kong: Skull Island" to 2017, "Godzilla: King of the Monsters" to 2019,
            "Godzilla vs. Kong" to 2021, "Godzilla x Kong: The New Empire" to 2024,
        ),
    ),
    Franchise(
        "indianajones", "Indiana Jones", "Adventure has a name",
        0xFF8D6E63 to 0xFF2A1C10,
        movies(
            "Raiders of the Lost Ark" to 1981, "Indiana Jones and the Temple of Doom" to 1984,
            "Indiana Jones and the Last Crusade" to 1989, "Indiana Jones and the Kingdom of the Crystal Skull" to 2008,
            "Indiana Jones and the Dial of Destiny" to 2023,
        ),
    ),
    Franchise(
        "terminator", "Terminator", "I'll be back",
        0xFF263238 to 0xFF2A0606,
        movies(
            "The Terminator" to 1984, "Terminator 2: Judgment Day" to 1991, "Terminator 3: Rise of the Machines" to 2003,
            "Terminator Salvation" to 2009, "Terminator Genisys" to 2015, "Terminator: Dark Fate" to 2019,
        ),
    ),
    Franchise(
        "alien", "Alien", "In space no one can hear you scream",
        0xFF33691E to 0xFF000000,
        movies(
            "Alien" to 1979, "Aliens" to 1986, "Alien 3" to 1992, "Alien: Resurrection" to 1997,
            "Prometheus" to 2012, "Alien: Covenant" to 2017, "Alien: Romulus" to 2024,
        ),
    ),
    Franchise(
        "apes", "Planet of the Apes", "The modern saga",
        0xFF6D4C41 to 0xFF1A120D,
        movies(
            "Rise of the Planet of the Apes" to 2011, "Dawn of the Planet of the Apes" to 2014,
            "War for the Planet of the Apes" to 2017, "Kingdom of the Planet of the Apes" to 2024,
        ),
    ),
    Franchise(
        "madmax", "Mad Max", "Fire and blood on the Fury Road",
        0xFFF57C00 to 0xFF331100,
        movies(
            "Mad Max" to 1979, "Mad Max 2" to 1981, "Mad Max Beyond Thunderdome" to 1985,
            "Mad Max: Fury Road" to 2015, "Furiosa: A Mad Max Saga" to 2024,
        ),
    ),
    Franchise(
        "rocky", "Rocky & Creed", "The heart of a champion",
        0xFFC62828 to 0xFF1A0505,
        movies(
            "Rocky" to 1976, "Rocky II" to 1979, "Rocky III" to 1982, "Rocky IV" to 1985, "Rocky V" to 1990,
            "Rocky Balboa" to 2006, "Creed" to 2015, "Creed II" to 2018, "Creed III" to 2023,
        ),
    ),
    Franchise(
        "bourne", "Jason Bourne", "He remembers everything",
        0xFF37474F to 0xFF0E1417,
        movies(
            "The Bourne Identity" to 2002, "The Bourne Supremacy" to 2004, "The Bourne Ultimatum" to 2007,
            "The Bourne Legacy" to 2012, "Jason Bourne" to 2016,
        ),
    ),
    Franchise(
        "bttf", "Back to the Future", "Great Scott!",
        0xFFFF8F00 to 0xFF0D2338,
        movies("Back to the Future" to 1985, "Back to the Future Part II" to 1989, "Back to the Future Part III" to 1990),
    ),
    Franchise(
        "mib", "Men in Black", "Protecting the Earth from the scum of the universe",
        0xFF212121 to 0xFF000000,
        movies("Men in Black" to 1997, "Men in Black II" to 2002, "Men in Black 3" to 2012, "Men in Black: International" to 2019),
    ),
    Franchise(
        "oceans", "Ocean's", "The perfect heists",
        0xFF795548 to 0xFF1C130F,
        movies("Ocean's Eleven" to 2001, "Ocean's Twelve" to 2004, "Ocean's Thirteen" to 2007, "Ocean's 8" to 2018),
    ),
    Franchise(
        "toystory", "Toy Story", "To infinity and beyond",
        0xFF1E88E5 to 0xFF0B2A4A,
        movies("Toy Story" to 1995, "Toy Story 2" to 1999, "Toy Story 3" to 2010, "Toy Story 4" to 2019, "Lightyear" to 2022, "Toy Story 5" to 2026),
    ),
    Franchise(
        "shrek", "Shrek", "Ogres, donkeys and Puss in Boots",
        0xFF7CB342 to 0xFF1B2A0B,
        movies(
            "Shrek" to 2001, "Shrek 2" to 2004, "Shrek the Third" to 2007, "Shrek Forever After" to 2010,
            "Puss in Boots" to 2011, "Puss in Boots: The Last Wish" to 2022,
        ),
    ),
    Franchise(
        "despicable", "Despicable Me & Minions", "Gru and his Minions",
        0xFFFBC02D to 0xFF0D47A1,
        movies(
            "Despicable Me" to 2010, "Despicable Me 2" to 2013, "Minions" to 2015, "Despicable Me 3" to 2017,
            "Minions: The Rise of Gru" to 2022, "Despicable Me 4" to 2024,
        ),
    ),
    Franchise(
        "kungfupanda", "Kung Fu Panda", "Skadoosh!",
        0xFFFF7043 to 0xFF2B120A,
        movies("Kung Fu Panda" to 2008, "Kung Fu Panda 2" to 2011, "Kung Fu Panda 3" to 2016, "Kung Fu Panda 4" to 2024),
    ),
    Franchise(
        "iceage", "Ice Age", "Manny, Sid, Diego and Scrat",
        0xFF4FC3F7 to 0xFF0A2633,
        movies(
            "Ice Age" to 2002, "Ice Age: The Meltdown" to 2006, "Ice Age: Dawn of the Dinosaurs" to 2009,
            "Ice Age: Continental Drift" to 2012, "Ice Age: Collision Course" to 2016,
        ),
    ),
    Franchise(
        "yrfspy", "YRF Spy Universe", "Tiger, Kabir and Pathaan",
        0xFFC62828 to 0xFF1A1A1A,
        movies("Ek Tha Tiger" to 2012, "Tiger Zinda Hai" to 2017, "War" to 2019, "Pathaan" to 2023, "Tiger 3" to 2023, "War 2" to 2025),
    ),
    Franchise(
        "maddock", "Maddock Horror Comedy Universe", "Stree, Bhediya, Munjya and more",
        0xFF6A1B9A to 0xFF12051C,
        movies("Stree" to 2018, "Bhediya" to 2022, "Munjya" to 2024, "Stree 2" to 2024, "Thamma" to 2025),
    ),
    Franchise(
        "dhoom", "Dhoom", "The heist trilogy",
        0xFFD84315 to 0xFF1F0A03,
        movies("Dhoom" to 2004, "Dhoom 2" to 2006, "Dhoom 3" to 2013),
    ),
    Franchise(
        "baahubali", "Baahubali", "Why did Kattappa kill Baahubali?",
        0xFFFFA000 to 0xFF2B1A00,
        movies("Baahubali: The Beginning" to 2015, "Baahubali 2: The Conclusion" to 2017),
    ),
    Franchise(
        "kgf", "K.G.F", "Rocky's rise in the gold fields",
        0xFFFFB300 to 0xFF140C00,
        movies("K.G.F: Chapter 1" to 2018, "K.G.F: Chapter 2" to 2022),
    ),
    Franchise(
        "pushpa", "Pushpa", "Flower nahi, fire hai",
        0xFFC62828 to 0xFF2A1405,
        movies("Pushpa: The Rise - Part 1" to 2021, "Pushpa 2: The Rule" to 2024),
    ),
    Franchise(
        "krrish", "Krrish", "India's own superhero",
        0xFF283593 to 0xFF0A0E26,
        movies("Koi... Mil Gaya" to 2003, "Krrish" to 2006, "Krrish 3" to 2013),
    ),
    Franchise(
        "golmaal", "Golmaal", "The comedy gang",
        0xFFFDD835 to 0xFF332B00,
        movies("Golmaal: Fun Unlimited" to 2006, "Golmaal Returns" to 2008, "Golmaal 3" to 2010, "Golmaal Again" to 2017),
    ),
    Franchise(
        "housefull", "Housefull", "Full house, full chaos",
        0xFFEC407A to 0xFF2B0A16,
        movies("Housefull" to 2010, "Housefull 2" to 2012, "Housefull 3" to 2016, "Housefull 4" to 2019, "Housefull 5" to 2025),
    ),
)

/**
 * Finds each franchise movie on Cinemeta by title and year, and remembers the matches so a
 * franchise opens instantly the next time.
 */
class FranchiseRepository(private val prefs: SharedPreferences, private val client: AddonClient) {
    private val cache: MutableMap<String, MetaPreview> = load()

    fun find(id: String): Franchise? = FRANCHISES.firstOrNull { it.id == id }

    /** Matches found so far, without asking the network; used for card artwork. */
    fun cached(f: Franchise): List<MetaPreview> = f.movies.mapNotNull { synchronized(cache) { cache[key(it)] } }

    suspend fun movies(f: Franchise): List<MetaPreview> = coroutineScope {
        val gate = Semaphore(6)
        val found = f.movies.map { m ->
            async {
                synchronized(cache) { cache[key(m)] }?.let { return@async it }
                gate.withPermit {
                    try {
                        resolve(m)?.also { synchronized(cache) { cache[key(m)] = it } }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.awaitAll()
        save()
        found.filterNotNull().distinctBy { it.id }
    }

    private suspend fun resolve(m: FranchiseMovie): MetaPreview? {
        val url = "$CINEMETA/catalog/movie/top/search=${Uri.encode(m.title)}.json"
        val results = JSONObject(client.getText(url)).optJSONArray("metas")
            .mapObjects { parsePreview(it, TYPE_MOVIE) }
            .filter { it.id.startsWith("tt") }
        val wanted = normalize(m.title)
        fun yearOf(p: MetaPreview) = p.releaseInfo?.take(4)?.toIntOrNull()
        return results.firstOrNull { normalize(it.name) == wanted && yearOf(it)?.let { y -> abs(y - m.year) <= 1 } != false }
            // Titles that differ slightly, like "Dune" listed as "Dune: Part One".
            ?: results.firstOrNull { yearOf(it) == m.year && (normalize(it.name).contains(wanted) || wanted.contains(normalize(it.name))) }
    }

    private fun normalize(s: String) = s.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }

    private fun key(m: FranchiseMovie) = "${m.title}|${m.year}"

    private fun save() {
        val o = JSONObject()
        synchronized(cache) {
            cache.forEach { (k, p) ->
                o.put(
                    k,
                    JSONObject()
                        .put("id", p.id)
                        .put("type", p.type)
                        .put("name", p.name)
                        .put("poster", p.poster ?: JSONObject.NULL)
                        .put("background", p.background ?: JSONObject.NULL)
                        .put("releaseInfo", p.releaseInfo ?: JSONObject.NULL)
                        .put("imdbRating", p.rating ?: JSONObject.NULL),
                )
            }
        }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private fun load(): MutableMap<String, MetaPreview> = try {
        val o = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        val map = HashMap<String, MetaPreview>()
        o.keys().forEach { k -> o.optJSONObject(k)?.let { parsePreview(it, TYPE_MOVIE) }?.let { map[k] = it } }
        map
    } catch (e: Exception) {
        HashMap()
    }

    companion object {
        private const val KEY = "franchise_matches_v1"
        private const val CINEMETA = "https://v3-cinemeta.strem.io"
    }
}
