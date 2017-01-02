package no.difi.statistics.query.elasticsearch.helpers;

import no.difi.statistics.ElasticsearchQueryServiceTest;
import no.difi.statistics.model.*;
import no.difi.statistics.test.utils.DataOperations;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static no.difi.statistics.test.utils.TimeSeriesSumCollector.summarize;

public class ThenFunction<T> implements Function<List<TimeSeries>, T> {

    private Function<List<TimeSeries>, T> function;

    public static SumHistogramBuilder sumHistogram() {
        return new SumHistogramBuilder();
    }

    public static SumBuilder sum() {
        return new SumBuilder();
    }

    public static LastPointBuilder lastPoint() {
        return new LastPointBuilder();
    }

    public static PercentileBuilder percentile() {
        return new PercentileBuilder();
    }

    public static AvailableSeriesBuilder availableSeries() {
        return new AvailableSeriesBuilder();
    }

    public interface Builder<T> {

        ThenFunction<T> build();

    }

    public static class SumHistogramBuilder implements Builder<List<TimeSeriesPoint>> {

        private MeasurementDistance targetDistance;
        private MeasurementDistance distance;

        public SumHistogramBuilder per(MeasurementDistance targetDistance) {
            this.targetDistance = targetDistance;
            return this;
        }

        public SumHistogramBuilder fromSeriesWithDistance(MeasurementDistance distance) {
            this.distance = distance;
            return this;
        }

        public ThenFunction<List<TimeSeriesPoint>> build() {
            ThenFunction<List<TimeSeriesPoint>> thenFunction = new ThenFunction<>();
            thenFunction.function = givenSeries -> DataOperations.sumPer(seriesForDistance(givenSeries, distance), targetDistance);
            return thenFunction;
        }

    }

    public static class SumBuilder implements Builder<List<TimeSeriesPoint>> {

        private ZonedDateTime from;
        private ZonedDateTime to;
        private MeasurementDistance distance;

        public SumBuilder from(ZonedDateTime from) {
            this.from = from;
            return this;
        }

        public SumBuilder fromSeriesWithDistance(MeasurementDistance distance) {
            this.distance = distance;
            return this;
        }

        public SumBuilder to(ZonedDateTime to) {
            this.to = to;
            return this;
        }

        @Override
        public ThenFunction<List<TimeSeriesPoint>> build() {
            ThenFunction<List<TimeSeriesPoint>> thenFunction = new ThenFunction<>();
            thenFunction.function =
                    givenSeries ->
                            singletonList(seriesForDistance(givenSeries, distance).getPoints()
                                    .stream().filter(this::withinRange).collect(summarize()));
            return thenFunction;
        }

        private boolean withinRange(TimeSeriesPoint point) {
            return !(from != null && point.getTimestamp().isBefore(from)) && !(to != null && point.getTimestamp().isAfter(to));
        }

    }

    public static class LastPointBuilder implements Builder<List<TimeSeriesPoint>> {

        private MeasurementDistance targetDistance;
        private MeasurementDistance distance;

        public LastPointBuilder per(MeasurementDistance targetDistance) {
            this.targetDistance = targetDistance;
            return this;
        }

        public LastPointBuilder fromSeriesWithDistance(MeasurementDistance distance) {
            this.distance = distance;
            return this;
        }

        public ThenFunction<List<TimeSeriesPoint>> build() {
            ThenFunction<List<TimeSeriesPoint>> thenFunction = new ThenFunction<>();
            thenFunction.function = givenSeries -> DataOperations.lastPer(seriesForDistance(givenSeries, distance), targetDistance);
            return thenFunction;
        }

    }

    public static class PercentileBuilder implements Builder<List<TimeSeriesPoint>> {

        private ElasticsearchQueryServiceTest.PercentileFilterBuilder percentileFilterBuilder;
        private MeasurementDistance distance;

        public PercentileBuilder fromSeriesWithDistance(MeasurementDistance distance) {
            this.distance = distance;
            return this;
        }

        public PercentileBuilder as(ElasticsearchQueryServiceTest.PercentileFilterBuilder percentileFilterBuilder) {
            this.percentileFilterBuilder = percentileFilterBuilder;
            return this;
        }

        public ThenFunction<List<TimeSeriesPoint>> build() {
            ThenFunction<List<TimeSeriesPoint>> thenFunction = new ThenFunction<>();
            thenFunction.function = givenSeries -> percentileFilterBuilder.apply(seriesForDistance(givenSeries, distance));
            return thenFunction;
        }

    }

    public static class AvailableSeriesBuilder implements Builder<List<TimeSeriesDefinition>> {

        @Override
        public ThenFunction<List<TimeSeriesDefinition>> build() {
            ThenFunction<List<TimeSeriesDefinition>> thenFunction = new ThenFunction<>();
            thenFunction.function = givenSeries -> givenSeries.stream().map(TimeSeries::getDefinition).sorted().collect(toList());
            return thenFunction;
        }
    }

    private static TimeSeries seriesForDistance(List<TimeSeries> givenSeries, MeasurementDistance distance) {
        if (distance != null)
            return givenSeries.stream().filter(s -> s.getDefinition().getDistance() == distance).findFirst().get();
        if (givenSeries.size() != 1)
            throw new IllegalArgumentException("Distance must be specified when number of given series is " + givenSeries.size());
        return givenSeries.get(0);
    }

    @Override
    public T apply(List<TimeSeries> timeSeriesPoints) {
        return function.apply(timeSeriesPoints);
    }
}