package mars.util;

public final strictfp class RandomIntSet8 {

    private static final int N = 8;

    private final int[] set = new int[N];
    private int n;

    public boolean isEmpty() {
        return n == 0;
    }

    public int pollRandom() {
        final int i = (int) (Math.random() * n);
        final int x = set[i];
        if (i != --n) set[i] = set[n];
        return x;
    }

    public void reset() {
        for (int i = 0; i < N; i++) set[i] = i;
        n = N;
    }

}
