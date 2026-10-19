package dev.mite.treasure;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Father Funzang's writings (EF-UI-09, ET-P-06, P05): written books with original project text (no MITE text), pages translated
 * through the lang files. Found in dungeon chests; reading one grants {@code hardscrabble:enlightenment}.
 */
public final class LoreBooks {
    private LoreBooks() {}

    public static final String AUTHOR = "Funzang";
    public static final String ACHIEVEMENT = "enlightenment";

    /** One book per row: EN title, FR title, then EN/FR page pairs. Read by MiteLang. */
    public static final String[][] TEXT = {
            {"Funzang: On Patience", "Funzang : de la patience",
                    "I am Funzang, once a brother of the quiet order. I write these pages for whoever wakes, as I did, with empty hands in a world that does not forgive.",
                    "Je suis Funzang, autrefois frère de l'ordre silencieux. J'écris ces pages pour qui s'éveille, comme moi, les mains vides dans un monde qui ne pardonne pas.",
                    "Do not mistake the land for a friend. Wood will not yield to bare fists, and the night belongs to those who were here first. Gather flint: a sharp stone is the start of all wisdom.",
                    "Ne prends pas la terre pour une amie. Le bois ne cède pas au poing nu, et la nuit appartient aux premiers venus. Ramasse du silex : une pierre tranchante est le début de toute sagesse.",
                    "Eat little, waste nothing, sleep when you can. Patience is the only tool that never breaks.\n\n- F.",
                    "Mange peu, ne gaspille rien, dors quand tu le peux. La patience est le seul outil qui ne se brise jamais.\n\n- F."},
            {"Funzang: On Metals", "Funzang : des métaux",
                    "Copper first, then silver and gold, then iron, which asks for a hotter fire. Each metal is a door, and each door needs the key forged before it.",
                    "Le cuivre d'abord, puis l'argent et l'or, puis le fer, qui exige un feu plus ardent. Chaque métal est une porte, et chaque porte demande la clé forgée avant elle.",
                    "Silver is pale and soft, yet the restless dead fear it. Wear it close and their cold touch slides away. I knew a man who kept his memories only because his helmet was silver.",
                    "L'argent est pâle et tendre, pourtant les morts sans repos le craignent. Porte-le sur toi et leur toucher glacé glisse. J'ai connu un homme qui garda sa mémoire grâce à son casque d'argent.",
                    "Beyond iron lie metals with older names. I held mithril once. I will not write where; the deep keeps its own counsel.\n\n- F.",
                    "Au-delà du fer viennent des métaux aux noms plus anciens. J'ai tenu du mithril une fois. Je n'écrirai pas où : les profondeurs gardent leurs secrets.\n\n- F."},
            {"Funzang: On the Deep", "Funzang : des profondeurs",
                    "Under the bedrock lies another world. Its sky is stone and its sun is lava. Those who go down seldom come back the same.",
                    "Sous la roche-mère s'étend un autre monde. Son ciel est de pierre et son soleil de lave. Ceux qui y descendent reviennent rarement les mêmes.",
                    "Lava does not merely burn. It eats plate and mail alike, and hot metal shields no one from the flame. Only the hardest metal of all shrugs it off.",
                    "La lave ne fait pas que brûler. Elle ronge la plaque comme la maille, et le métal brûlant ne protège personne de la flamme. Seul le plus dur des métaux lui résiste.",
                    "Knowledge, like experience, can be spent. Mint it, bottle it, or carry it in your head. But spend it, for the dead carry nothing.\n\n- F.",
                    "Le savoir, comme l'expérience, se dépense. Frappe-le en pièces, mets-le en bouteille ou garde-le en tête. Mais dépense-le, car les morts n'emportent rien.\n\n- F."},
    };

    public static String titleKey(int book) {
        return "book.hardscrabble.funzang." + book + ".title";
    }

    public static String pageKey(int book, int page) {
        return "book.hardscrabble.funzang." + book + ".page" + page;
    }

    public static int pages(int book) {
        return (TEXT[book].length - 2) / 2;
    }

    public static WrittenBookContent content(int book) {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int p = 0; p < pages(book); p++) pages.add(Filterable.passThrough(Component.translatable(pageKey(book, p))));
        return new WrittenBookContent(Filterable.passThrough(""), AUTHOR, 0, pages, true); // blank title: ITEM_NAME is shown
    }

    /**
     * A Funzang book (TreasureData.lorePool): the author and the translated title in its item name. A player named Funzang signs
     * books with that author, but survival cannot set an item name (MITE anvils do not rename), so the name is the marker.
     */
    public static boolean isLore(ItemStack stack) {
        WrittenBookContent c = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        Component name = stack.get(DataComponents.ITEM_NAME);
        return stack.is(Items.WRITTEN_BOOK) && c != null && AUTHOR.equals(c.author()) && name != null
                && name.getContents() instanceof TranslatableContents t && t.getKey().startsWith("book.hardscrabble.funzang.");
    }
}
