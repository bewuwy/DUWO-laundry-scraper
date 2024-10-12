# DUWO laundry bot

Simple Java program to check availability of washing machines and dryers in DUWO buildings.

## Usage

1. Create `config.txt` with:
```
user=YOUR_MULTIPOSS@EMAIL.COM
```

2. Run `java -jar DUWO_laundry.jar`

3. You can also also use CLI arguments:
   - `wm` for washing machine
   - `d` for dryer
   - add numbers after the machine name to quantify the target

### Examples

- `java -jar DUWO_laundry.jar wm` - for 1 washing machine
- `java -jar DUWO_laundry.jar wm d` - for 1 washing machine and 1 dryer
- `java -jar DUWO_laundry.jar "Washing Machine" 3` - for 3 washing machines
- `java -jar DUWO_laundry.jar wm 10 Dryer 20` - for 10 washing machines and 20 dryers
