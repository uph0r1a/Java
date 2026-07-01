package module;

import java.util.Comparator;

public class DeviceNameComparator implements Comparator<ElectronicDevice> {
    @Override
    public int compare(ElectronicDevice d1, ElectronicDevice d2) {
        return d1.getName().compareToIgnoreCase(d2.getName());
    }
}