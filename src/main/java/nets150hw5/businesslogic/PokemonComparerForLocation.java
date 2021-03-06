package nets150hw5.businesslogic;

import org.apache.commons.lang3.tuple.ImmutablePair;
import nets150hw5.datamodel.PokeAPILocation;
import nets150hw5.datamodel.PokeAPIPokemon;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class PokemonComparerForLocation {
    private final TypeMatchupGraphs typeGraphs;              // used to include type relationships between pokemon
    private final PokemonInfoRetriever pokemonInfoRetriever; // used to get info about pokemon
    private final PokeAPILocation location;

    private final Map<PokeAPIPokemon, List<ImmutablePair<PokeAPIPokemon, Double>>> network;

    public PokemonComparerForLocation(final TypeMatchupGraphs typeGraphs,
                                      final PokemonInfoRetriever pokemonInfoRetriever,
                                      final PokeAPILocation location) {
        this.typeGraphs = checkNotNull(typeGraphs);
        this.pokemonInfoRetriever = checkNotNull(pokemonInfoRetriever);
        this.location = checkNotNull(location);

        this.network = createPokemonNetworkForLocation();
        
    }

    private Map<PokeAPIPokemon, List<ImmutablePair<PokeAPIPokemon, Double>>> createPokemonNetworkForLocation() {
        Map<PokeAPIPokemon, List<ImmutablePair<PokeAPIPokemon, Double>>> result = new HashMap<>();
        final List<PokeAPIPokemon> allPokemonFromLocation = pokemonInfoRetriever.getPokemonForLocation(location);
//        System.out.println(allPokemonFromLocation);
        for (PokeAPIPokemon keyPokemon : allPokemonFromLocation) {
            final List<ImmutablePair<PokeAPIPokemon, Double>> valueList = new ArrayList<>(allPokemonFromLocation.size()-1);
            for (PokeAPIPokemon valPokemon : allPokemonFromLocation) {
                if (keyPokemon.equals(valPokemon)) continue;

                List<String> keyTypes = keyPokemon.getTypes();
                List<String> valTypes = valPokemon.getTypes();

                double totalEffect = 0.0;
                for (String keyType : keyTypes) {
                    final Set<String> zeroTipmes = typeGraphs.getZeroTimes(keyType);
                    final Set<String> halfTimes = typeGraphs.getHalfTimes(keyType);
                    final Set<String> twoTipmes = typeGraphs.getTwoTimes(keyType);
                    for (String valType : valTypes) {
                        if (zeroTipmes.contains(valType)) continue;
                        else if (halfTimes.contains(valType)) totalEffect += 0.5;
                        else if (twoTipmes.contains(valType)) totalEffect += 2.0;
                        else totalEffect += 1.0;
                    }
                }
                totalEffect /= (keyTypes.size() * valTypes.size());

                final ImmutablePair<PokeAPIPokemon, Double> valPair = new ImmutablePair<>(valPokemon, totalEffect);
                valueList.add(valPair);
            }
            result.put(keyPokemon, valueList);
        }
//        System.out.println(result);
        return Collections.unmodifiableMap(result);
    }

    private Map<PokeAPIPokemon, Double> scorePokemon() {
        final Map<PokeAPIPokemon, Double> scores = new HashMap<>();
        final Map<PokeAPIPokemon, Double> outSums = new HashMap<>();
        final Map<PokeAPIPokemon, Double> inSums = new HashMap<>();

        final PokeAPIPokemon start = network
                .keySet()
                .stream()
                .findAny()
                .get();
        
        final Set<PokeAPIPokemon> visited = new HashSet<>();
        final List<PokeAPIPokemon> queue = new LinkedList<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            final PokeAPIPokemon currPokemon = queue.remove(0);

            if (visited.contains(currPokemon)) continue;

            final List<ImmutablePair<PokeAPIPokemon, Double>> others = this.network.get(currPokemon);

            double currOutSum = 0.0;
            for (ImmutablePair<PokeAPIPokemon, Double> other : others) {
                final PokeAPIPokemon otherPokemon = other.getKey();
                final double currOutWeight = other.getValue();

                if (!inSums.containsKey(otherPokemon)) inSums.put(otherPokemon, 0.0);

                currOutSum += currOutWeight;

                final double prevInSum = inSums.get(otherPokemon);
                inSums.put(otherPokemon, prevInSum + currOutWeight);
                queue.add(otherPokemon);
            }
            outSums.put(currPokemon, currOutSum);

            visited.add(currPokemon);
        }

        for (PokeAPIPokemon pokemon : outSums.keySet()) {
            final double outAvg = outSums.get(pokemon) / (double) (outSums.keySet().size() - 1);
            final double inAvg = inSums.get(pokemon) / (double) (inSums.keySet().size() - 1);

            final double score = pokemon.getRawScore() * outAvg / inAvg;
            scores.put(pokemon, score);
        }

        return Collections.unmodifiableMap(scores);
    }

    private List<PokeAPIPokemon> orderPokemon(final Map<PokeAPIPokemon, Double> scores) {
        final List<PokeAPIPokemon> ranking = scores.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        Collections.reverse(ranking);

        return Collections.unmodifiableList(ranking);
    }

    public List<PokeAPIPokemon> rankPokemon() {
        final Map<PokeAPIPokemon, Double> scores = this.scorePokemon();
        return orderPokemon(scores);
    }
}
