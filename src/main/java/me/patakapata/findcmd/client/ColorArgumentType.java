package me.patakapata.findcmd.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class ColorArgumentType implements ArgumentType<Color> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0 0 0 255", "255 255 0 127", "214 60 123 44");

    public ColorArgumentType() {
    }

    public static Color getColor(CommandContext<?> ctx, String name) {
        return ctx.getArgument(name, Color.class);
    }

    public static ColorArgumentType color() {
        return new ColorArgumentType();
    }

    @Override
    public Color parse(StringReader reader) throws CommandSyntaxException {
        int red = reader.readInt();
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            int green = reader.readInt();
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                int blue = reader.readInt();
                if (reader.canRead() && reader.peek() == ' ') {
                    reader.skip();
                    int alpha = reader.readInt();
                    return new Color(alpha, red, green, blue);
                }
            }
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().create();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        if ("255 255 255 255".startsWith(remaining)) {
            builder.suggest("255 255 255 255");
        }
        if ("0 0 0 255".startsWith(remaining)) {
            builder.suggest("0 0 0 255");
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
