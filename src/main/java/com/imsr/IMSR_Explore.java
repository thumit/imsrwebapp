package com.imsr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IMSR_Explore {

    // Define standard headers used throughout data aggregation
    public static final String[] HEADER_NATIONAL = new String[] { 
        "imsr_date", "preparedness_level", "initial_attack_activity",
        "new_fires", "new_large_fires", "contained_large_fires", "uncontained_large_fires", 
        "area_command_teams", "nimos", "type_1_teams", "type_2_teams", "fire_use_teams", "complex_teams" 
    };
    
    public static final String[] HEADER_GACC = new String[] { 
        "imsr_date", "gacc", "gacc_priority", "preparedness_level", "new_fires",
        "new_large_fires", "uncontained_large_fires", "area_command_teams", "nimos", 
        "type_1_teams", "type_2_teams", "fire_use_teams", "complex_teams" 
    };
    
    public static final String[] HEADER_WILDFIRE = new String[] { 
        "imsr_date", "gacc", "gacc_priority", "fire_priority", "new_large_fire_mark", "fire_name", "unit", "fire_size",
        "fire_size_change", "percent_containment", "contained_completed", "estimated_containment_date", "personnel", "personnel_change", "crews",
        "engines", "helicopters", "structures_lost", "cost_to_date", "origin_ownership" 
    };
    
    public static final String[] HEADER_RESOURCE = new String[] { 
        "imsr_date", "gacc", "incidents", "cumulative_size", "crews", "engines", "helicopters", "personnel", "personnel_change", "preparedness_level" 
    };

    /**
     * Core aggregation logic preserving your precise data processing and fix_ctd algorithm.
     */
    public static AggregationResult aggregateFiles(File[] s_files, File[] r_files) {
        IMSR_Process[] ismr_process = new IMSR_Process[s_files.length];
        for (int i = 0; i < s_files.length; i++) {
            ismr_process[i] = new IMSR_Process(s_files[i], r_files[i]);
        }

        List<String> nationalActivity = new ArrayList<>();
        nationalActivity.add(String.join("\t", HEADER_NATIONAL));
        for (IMSR_Process ismr : ismr_process) {
            nationalActivity.add(String.join("\t", ismr.national_activity));
        }

        List<String> gaccActivity = new ArrayList<>();
        gaccActivity.add(String.join("\t", HEADER_GACC));
        for (IMSR_Process ismr : ismr_process) {
            for (String st : ismr.gacc_activity) {
                gaccActivity.add(st);
            }
        }

        List<String> resourceSummary = new ArrayList<>();
        resourceSummary.add(String.join("\t", HEADER_RESOURCE));
        for (IMSR_Process ismr : ismr_process) {
            for (String st : ismr.resource_summary) {
                resourceSummary.add(st);
            }
        }

        List<String> finalFires = new ArrayList<>();
        for (IMSR_Process ismr : ismr_process) {
            finalFires.addAll(ismr.final_fires);
        }
        
        // Apply your precise cost-to-date fixing logic
        List<String> rawNumberLog = fix_ctd(finalFires);

        List<String> wildfireActivity = new ArrayList<>();
        wildfireActivity.add(String.join("\t", HEADER_WILDFIRE));
        wildfireActivity.addAll(finalFires);

        return new AggregationResult(nationalActivity, gaccActivity, wildfireActivity, resourceSummary, rawNumberLog);
    }

    /**
     * Exact implementation of your fix_ctd method preserving all original rules.
     */
    public static List<String> fix_ctd(List<String> final_fires) {
        List<String> logs = new ArrayList<>();
        String raw_number_record_list = "";
        
        // Loop forward and fix using previous fire
        for (int i = 0; i < final_fires.size(); i++) {
            String[] fs = final_fires.get(i).split("\t");
            if (fs[18].endsWith("KI")) {
                fs[18] = fs[18].substring(0, fs[18].length() - 1); 
                String adjusted_fire = String.join("\t", fs);
                final_fires.set(i, adjusted_fire);
            }
            if (fs[18].equals("7/18")) {
                fs[18] = "NR"; 
                String adjusted_fire = String.join("\t", fs);
                final_fires.set(i, adjusted_fire);
            }
            if (fs[19].equals("3.8M")) {
                fs[19] = "FS"; 
                String adjusted_fire = String.join("\t", fs);
                final_fires.set(i, adjusted_fire);
            }
            
            try {
                if (!(fs[18].equals("NA") || fs[18].equals("NR") || fs[18].equals("---") || fs[18].endsWith("K") || fs[18].endsWith("M"))) {
                    boolean continue_loop = true;
                    int l = i;
                    do {
                        l = l - 1;
                        String[] previous_fs = final_fires.get(l).split("\t");
                        if (previous_fs[5].equals(fs[5]) && (previous_fs[18].endsWith("K") || previous_fs[18].endsWith("M"))) {        
                            double previous_ctd = Double.valueOf(previous_fs[18].substring(0, previous_fs[18].length() - 1));
                            double ctd = Double.valueOf(fs[18]);
                            if (previous_ctd <= ctd) {
                                fs[18] = fs[18] + previous_fs[18].substring(previous_fs[18].length() - 1);        
                            } else {
                                fs[18] = fs[18] + "M";    
                            }
                            String adjusted_fire = String.join("\t", fs);
                            final_fires.set(i, adjusted_fire);
                            logs.add(String.join("\t", fs[0], fs[1], fs[4], fs[5], "cost_to_date: K or M added"));
                            continue_loop = false;
                        }
                    } while (continue_loop && l > 0);
                }
            } catch (NumberFormatException e) {
                logs.add("Problem when trying to fix the cost " + final_fires.get(i));
            }
        }
        
        // Loop backward and fix using next fire
        for (int i = final_fires.size() - 1; i >= 0; i--) {
            String[] fs = final_fires.get(i).split("\t");
            try {
                if (!(fs[18].equals("NA") || fs[18].equals("NR") || fs[18].equals("---") || fs[18].endsWith("K") || fs[18].endsWith("M"))) {
                    boolean continue_loop = true;
                    int l = i;
                    do {
                        l = l + 1;
                        String[] next_fs = final_fires.get(l).split("\t");
                        if (next_fs[5].equals(fs[5]) && (next_fs[18].endsWith("K") || next_fs[18].endsWith("M"))) {        
                            double next_ctd = Double.valueOf(next_fs[18].substring(0, next_fs[18].length() - 1));
                            double ctd = Double.valueOf(fs[18]);
                            if (next_ctd >= ctd) {
                                fs[18] = fs[18] + next_fs[18].substring(next_fs[18].length() - 1);        
                            } else {
                                fs[18] = fs[18] + "K";    
                            }
                            String adjusted_fire = String.join("\t", fs);
                            final_fires.set(i, adjusted_fire);
                            logs.add(String.join("\t", fs[0], fs[1], fs[4], fs[5], "cost_to_date: K or M added"));
                            continue_loop = false;
                        }
                    } while (continue_loop && l < final_fires.size() - 1);
                    if (continue_loop) {
                        String raw_number_record = String.join("\t", fs[0], fs[1], fs[4], fs[5], "cost_to_date: unchanged because cannot identify K or M");
                        logs.add(raw_number_record);
                    }
                }
            } catch (NumberFormatException e) {
                logs.add("Problem when trying to fix the cost " + final_fires.get(i));
            }
        }
        return logs;
    }

    /**
     * Simple container class for holding the aggregated results safely.
     */
    public static class AggregationResult {
        public List<String> nationalActivity;
        public List<String> gaccActivity;
        public List<String> wildfireActivity;
        public List<String> resourceSummary;
        public List<String> logs;

        public AggregationResult(List<String> nationalActivity, List<String> gaccActivity, 
                                 List<String> wildfireActivity, List<String> resourceSummary, List<String> logs) {
            this.nationalActivity = nationalActivity;
            this.gaccActivity = gaccActivity;
            this.wildfireActivity = wildfireActivity;
            this.resourceSummary = resourceSummary;
            this.logs = logs;
        }
    }
}