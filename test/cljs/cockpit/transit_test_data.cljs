(ns cockpit.transit-test-data)

(def stop-payload
  {:id "MTASBWY:142N"
   :name "South Ferry"
   :mtaStopId "18000"
   :lonSet true
   :latSet true
   :lat 40.702066
   :lon -74.013666
   :parentStation "142"
   :vehicleTypeSet false
   :vehicleType -999
   :wheelchairBoarding 0
   :locationType 0
   :regionalFareCardAccepted 0})

(def route-payload
  {:eligibilityRestricted -999
   :desc
   "Trains operate between 242 St in the Bronx and South Ferry in Manhattan, at all times"
   :color "EE352E"
   :agency {:id "MTASBWY"
            :name "MTA New York City Transit"
            :url "http://www.mta.info"
            :timezone "America/New_York"
            :phone "718-330-1234"}
   :routeBikesAllowed 0
   :type 1
   :bikesAllowed 0
   :shortName "1"
   :textColor "000000"
   :id "MTASBWY:1"
   :regionalFareCardAccepted 0
   :url "http://web.mta.info/nyct/service/pdf/t1cur.pdf"
   :sortOrder 1
   :longName "Broadway - 7 Avenue Local"
   :sortOrderSet true})

(def stop-times-payload
  [{:route {:id "MTASBWY:1"}
    :times
    [{:departureDelay 0
      :stopName "South Ferry"
      :stopCount 38
      :stopLon -74.013666
      :scheduledArrival 89130
      :realtimeArrival 89130
      :realtime false
      :scheduledDeparture 89130
      :stopId "MTASBWY:142N"
      :departureFmt "2020-06-20T00:45:30-04:00"
      :directionId "0"
      :regionalFareCardAccepted false
      :stopLat 40.702066
      :serviceDay 1592539200
      :tripId "MTASBWY:5953"
      :stopIndex 0
      :timepoint true
      :peakOffpeak 0
      :realtimeState "SCHEDULED"
      :realtimeDeparture 89130
      :arrivalDelay 0
      :arrivalFmt "2020-06-20T00:45:30-04:00"
      :stopHeadsign "Uptown & The Bronx"
      :tripHeadsign "Van Cortlandt Park - 242 St"}]}
   {:route {:id "MTASBWY:1"}
    :times
    [{:departureDelay 0
      :stopName "South Ferry"
      :stopCount 38
      :stopLon -74.013666
      :scheduledArrival 84540
      :realtimeSignText ""
      :realtimeArrival -1
      :realtime true
      :scheduledDeparture 84540
      :stopId "MTASBWY:142N"
      :departureFmt "2020-06-19T23:29:00-04:00"
      :directionId "0"
      :regionalFareCardAccepted false
      :stopLat 40.702066
      :serviceDay 1592539200
      :tripId "MTASBWY:2350"
      :stopIndex 0
      :timepoint true
      :peakOffpeak 0
      :realtimeState "UPDATED"
      :timestamp 1592623298
      :realtimeDeparture 84540
      :arrivalDelay -84541
      :stopHeadsign "Uptown & The Bronx"
      :tripHeadsign "Van Cortlandt Park - 242 St"
      :track "1"}
     {:departureDelay 0
      :stopName "South Ferry"
      :stopCount 38
      :stopLon -74.013666
      :scheduledArrival 86340
      :realtimeArrival 86340
      :realtime false
      :scheduledDeparture 86340
      :stopId "MTASBWY:142N"
      :departureFmt "2020-06-19T23:59:00-04:00"
      :directionId "0"
      :regionalFareCardAccepted false
      :stopLat 40.702066
      :serviceDay 1592539200
      :tripId "MTASBWY:2353"
      :stopIndex 0
      :timepoint true
      :peakOffpeak 0
      :realtimeState "SCHEDULED"
      :realtimeDeparture 86340
      :arrivalDelay 0
      :arrivalFmt "2020-06-19T23:59:00-04:00"
      :stopHeadsign "Uptown & The Bronx"
      :tripHeadsign "Van Cortlandt Park - 242 St"}]}
   {:route {:id "MTASBWY:1"}
    :times
    [{:departureDelay 0
      :stopName "South Ferry"
      :stopCount 38
      :stopLon -74.013666
      :scheduledArrival 86940
      :realtimeArrival 86940
      :realtime false
      :scheduledDeparture 86940
      :stopId "MTASBWY:142N"
      :departureFmt "2020-06-20T00:09:00-04:00"
      :directionId "0"
      :regionalFareCardAccepted false
      :stopLat 40.702066
      :serviceDay 1592539200
      :tripId "MTASBWY:5950"
      :stopIndex 0
      :timepoint true
      :peakOffpeak 0
      :realtimeState "SCHEDULED"
      :realtimeDeparture 86940
      :arrivalDelay 0
      :arrivalFmt "2020-06-20T00:09:00-04:00"
      :stopHeadsign "Uptown & The Bronx"
      :tripHeadsign "Van Cortlandt Park - 242 St"}]}
   {:route {:id "MTASBWY:1"}
    :times
    [{:departureDelay 0
      :stopName "South Ferry"
      :stopCount 38
      :stopLon -74.013666
      :scheduledArrival 88320
      :realtimeArrival 88320
      :realtime false
      :scheduledDeparture 88320
      :stopId "MTASBWY:142N"
      :departureFmt "2020-06-20T00:32:00-04:00"
      :directionId "0"
      :regionalFareCardAccepted false
      :stopLat 40.702066
      :serviceDay 1592539200
      :tripId "MTASBWY:5952"
      :stopIndex 0
      :timepoint true
      :peakOffpeak 0
      :realtimeState "SCHEDULED"
      :realtimeDeparture 88320
      :arrivalDelay 0
      :arrivalFmt "2020-06-20T00:32:00-04:00"
      :stopHeadsign "Uptown & The Bronx"
      :tripHeadsign "Van Cortlandt Park - 242 St"}]}
   {:route {:id "MTASBWY:1"}
    :times
    [{:departureDelay 0
      :stopName "South Ferry"
      :stopCount 38
      :stopLon -74.013666
      :scheduledArrival 87540
      :realtimeArrival 87540
      :realtime false
      :scheduledDeparture 87540
      :stopId "MTASBWY:142N"
      :departureFmt "2020-06-20T00:19:00-04:00"
      :directionId "0"
      :regionalFareCardAccepted false
      :stopLat 40.702066
      :serviceDay 1592539200
      :tripId "MTASBWY:5951"
      :stopIndex 0
      :timepoint true
      :peakOffpeak 0
      :realtimeState "SCHEDULED"
      :realtimeDeparture 87540
      :arrivalDelay 0
      :arrivalFmt "2020-06-20T00:19:00-04:00"
      :stopHeadsign "Uptown & The Bronx"
      :tripHeadsign "Van Cortlandt Park - 242 St"}]}])

(def fallback-payload
  {:lastUpdatedTime "4:05:15 pm",
  :lastUpdatedOn "1592683515",
  :stationName "South Ferry",
  :direction1
  {:name "Uptown",
   :times
   [{:route "1",
     :lastStation "Van Cortlandt Park - 242 St",
     :minutes 1}
    {:route "1",
     :lastStation "Van Cortlandt Park - 242 St",
     :minutes 31}
    {:route "1",
     :lastStation "Van Cortlandt Park - 242 St",
     :minutes 41}
    {:route "1",
     :lastStation "Van Cortlandt Park - 242 St",
     :minutes 51}]},
  :direction2
  {:name "Downtown",
   :times
   [{:route "1", :lastStation "South Ferry", :minutes 3}
    {:route "1", :lastStation "South Ferry", :minutes 11}
    {:route "1", :lastStation "South Ferry", :minutes 19}
    {:route "1", :lastStation "South Ferry", :minutes 28}]},
  :message
  {:message "XML Doc created successfully",
   :messageType "SUCCESS",
   :errorCode "0"}})
