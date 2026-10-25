import type { MachineType, PmKind } from '../../api/types'

/**
 * A starting routine for each kind of machine, to choose from when setting up preventive jobs. They are common
 * practice in tile plants, not rules: change the interval or the words to fit your machines before saving.
 * Titles are stored as typed, so each suggestion carries its English and Gujarati words.
 */
export interface PmSuggestion {
  kind: PmKind
  everyDays: number
  needsStop: boolean
  en: string
  gu: string
}

export const PM_SUGGESTIONS: Partial<Record<MachineType, PmSuggestion[]>> = {
  BALL_MILL: [
    { kind: 'LUBRICATION', everyDays: 7, needsStop: false, en: 'Grease the main bearings', gu: 'મુખ્ય બેરિંગમાં ગ્રીસ પૂરો' },
    { kind: 'INSPECTION', everyDays: 30, needsStop: true, en: 'Check lining and grinding media wear', gu: 'લાઇનિંગ અને ગ્રાઇન્ડિંગ મીડિયાનો ઘસારો તપાસો' },
    { kind: 'INSPECTION', everyDays: 30, needsStop: false, en: 'Check gearbox oil level', gu: 'ગિયરબોક્સ ઓઇલ લેવલ તપાસો' },
  ],
  SPRAY_DRYER: [
    { kind: 'CLEANING', everyDays: 7, needsStop: true, en: 'Clean nozzles and slip filters', gu: 'નોઝલ અને સ્લિપ ફિલ્ટર સાફ કરો' },
    { kind: 'INSPECTION', everyDays: 30, needsStop: false, en: 'Check burner and flame', gu: 'બર્નર અને જ્યોત તપાસો' },
    { kind: 'INSPECTION', everyDays: 30, needsStop: true, en: 'Inspect cyclone and bag filter', gu: 'સાયક્લોન અને બેગ ફિલ્ટર તપાસો' },
  ],
  PRESS: [
    { kind: 'INSPECTION', everyDays: 1, needsStop: false, en: 'Check hydraulic oil level and temperature', gu: 'હાઇડ્રોલિક ઓઇલ લેવલ અને તાપમાન તપાસો' },
    { kind: 'CLEANING', everyDays: 7, needsStop: true, en: 'Clean die and punches', gu: 'ડાઇ અને પંચ સાફ કરો' },
    { kind: 'LUBRICATION', everyDays: 7, needsStop: false, en: 'Grease the press guides', gu: 'પ્રેસ ગાઇડમાં ગ્રીસ પૂરો' },
    { kind: 'REPLACEMENT', everyDays: 90, needsStop: true, en: 'Change hydraulic oil filter', gu: 'હાઇડ્રોલિક ઓઇલ ફિલ્ટર બદલો' },
  ],
  DRYER: [
    { kind: 'CLEANING', everyDays: 7, needsStop: false, en: 'Clean rollers and check the drive chain', gu: 'રોલર સાફ કરો અને ડ્રાઇવ ચેઇન તપાસો' },
    { kind: 'INSPECTION', everyDays: 30, needsStop: false, en: 'Check burners and fans', gu: 'બર્નર અને પંખા તપાસો' },
  ],
  GLAZE_LINE: [
    { kind: 'CLEANING', everyDays: 1, needsStop: false, en: 'Clean bells, spray guns and screens', gu: 'બેલ, સ્પ્રે ગન અને જાળી સાફ કરો' },
    { kind: 'INSPECTION', everyDays: 7, needsStop: false, en: 'Check belts and pulleys', gu: 'બેલ્ટ અને પુલી તપાસો' },
  ],
  DIGITAL_PRINTER: [
    { kind: 'CLEANING', everyDays: 1, needsStop: false, en: 'Print head nozzle check and purge', gu: 'પ્રિન્ટ હેડ નોઝલ ચેક અને પર્જ' },
    { kind: 'INSPECTION', everyDays: 7, needsStop: false, en: 'Check ink circuit pressure and temperature', gu: 'ઇંક સર્કિટનું પ્રેશર અને તાપમાન તપાસો' },
    { kind: 'REPLACEMENT', everyDays: 30, needsStop: true, en: 'Change ink filters', gu: 'ઇંક ફિલ્ટર બદલો' },
  ],
  KILN: [
    { kind: 'INSPECTION', everyDays: 7, needsStop: false, en: 'Check rollers, replace broken ones', gu: 'રોલર તપાસો, તૂટેલા બદલો' },
    { kind: 'LUBRICATION', everyDays: 7, needsStop: false, en: 'Grease the roller drive', gu: 'રોલર ડ્રાઇવમાં ગ્રીસ પૂરો' },
    { kind: 'INSPECTION', everyDays: 30, needsStop: false, en: 'Check burners and thermocouples', gu: 'બર્નર અને થર્મોકપલ તપાસો' },
    { kind: 'CALIBRATION', everyDays: 90, needsStop: false, en: 'Calibrate thermocouples', gu: 'થર્મોકપલ કેલિબ્રેટ કરો' },
  ],
  POLISHING_LINE: [
    { kind: 'INSPECTION', everyDays: 1, needsStop: false, en: 'Check polishing heads and abrasives', gu: 'પોલિશિંગ હેડ અને એબ્રેસિવ તપાસો' },
    { kind: 'LUBRICATION', everyDays: 7, needsStop: false, en: 'Grease the spindles', gu: 'સ્પિન્ડલમાં ગ્રીસ પૂરો' },
    { kind: 'INSPECTION', everyDays: 7, needsStop: false, en: 'Check the water system', gu: 'પાણીની સિસ્ટમ તપાસો' },
  ],
  SORTING_LINE: [
    { kind: 'CALIBRATION', everyDays: 7, needsStop: true, en: 'Clean sensors, calibrate planarity and caliber', gu: 'સેન્સર સાફ કરો, પ્લેનારિટી અને કેલિબર કેલિબ્રેટ કરો' },
  ],
  PACKING_LINE: [
    { kind: 'INSPECTION', everyDays: 7, needsStop: false, en: 'Check the strapping and stacking units', gu: 'સ્ટ્રેપિંગ અને સ્ટેકિંગ યુનિટ તપાસો' },
  ],
  UTILITY: [
    { kind: 'CLEANING', everyDays: 1, needsStop: false, en: 'Drain moisture from the compressor and air tank', gu: 'કોમ્પ્રેસર અને એર ટેન્કમાંથી ભેજ કાઢો' },
    { kind: 'REPLACEMENT', everyDays: 30, needsStop: true, en: 'Change the air filter', gu: 'એર ફિલ્ટર બદલો' },
    { kind: 'REPLACEMENT', everyDays: 90, needsStop: true, en: 'Change the compressor oil', gu: 'કોમ્પ્રેસર ઓઇલ બદલો' },
  ],
}
